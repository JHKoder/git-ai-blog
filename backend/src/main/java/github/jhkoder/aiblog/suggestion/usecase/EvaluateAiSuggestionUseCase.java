package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.AiClientRouter;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.prompt.PromptBuilder;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 평가 요청을 SSE 스트리밍으로 처리한다.
 * - 평가 결과는 DB에 저장하지 않고 프론트에만 전달한다.
 * - 스트리밍 완료 시 __done__ 신호를 emit한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluateAiSuggestionUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiClientRouter aiClientRouter;
    private final PromptBuilder promptBuilder;

    private static final int EVAL_HEAD_CHARS = 2_000;
    private static final int EVAL_TAIL_CHARS = 500;

    private static final Map<String, Long> FALLBACK_SECONDS = Map.of(
            "claude-sonnet-4-6", 40L,
            "claude-opus-4-5",   60L,
            "grok-3",            20L,
            "gpt-4o",            30L,
            "gpt-4o-mini",       20L,
            "gemini-2.0-flash",  15L
    );

    @Transactional(readOnly = true)
    public Flux<String> evaluate(Long postId, Long memberId, AiSuggestionRequest request) {
        log.info("[EvalAI][1] 요청 수신 postId={} memberId={} model={}", postId, memberId, request.getModel());
        aiUsageLimiter.check(memberId);

        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        String content = sampleContent(post.getContent());
        String prompt = promptBuilder.buildEvaluation(post.getContentType(), content);
        AiClientRouter.RouteResult route = aiClientRouter.route(post.getContentType(), request.getModel(), member);
        log.info("[EvalAI][2] 라우팅 완료 model={} promptLen={}", route.model(), prompt.length());

        long estimatedSec = FALLBACK_SECONDS.getOrDefault(route.model(), 30L);
        AtomicLong tokenCount = new AtomicLong(0);

        Flux<String> estimatedEvent = Flux.just("__estimated__:" + estimatedSec);
        Flux<String> tokenStream = route.client().streamComplete(prompt, route.model(), route.apiKey())
                .doOnSubscribe(s -> log.info("[EvalAI][3] AI API 구독 시작"))
                .doOnNext(token -> {
                    long cnt = tokenCount.incrementAndGet();
                    if (cnt == 1) log.info("[EvalAI][4] 첫 토큰 수신");
                })
                .doOnComplete(() -> log.info("[EvalAI][5] 평가 스트림 완료 총 {}개", tokenCount.get()))
                .doOnError(e -> log.error("[EvalAI][ERR] 평가 스트리밍 실패: {}", e.getMessage(), e));

        // 평가 완료 후 사용량 기록 + __done__ emit (DB 저장 없음)
        Flux<String> doneSignal = Flux.defer(() -> {
            log.info("[EvalAI][6] 평가 완료 → 사용량 기록 + __done__ emit");
            try {
                aiUsageLimiter.increment(memberId);
            } catch (Exception e) {
                log.error("[EvalAI][ERR] 사용량 기록 실패: {}", e.getMessage());
            }
            return Flux.just("__done__");
        });

        return estimatedEvent.concatWith(tokenStream).concatWith(doneSignal);
    }

    /**
     * 평가용 본문 샘플링 — 앞 2,000자 + 뒤 500자만 전송.
     * 전체 본문 불필요, 평가 품질 동일하면서 토큰 절약.
     */
    private String sampleContent(String content) {
        if (content == null || content.length() <= EVAL_HEAD_CHARS + EVAL_TAIL_CHARS) {
            return content;
        }
        String head = content.substring(0, EVAL_HEAD_CHARS);
        String tail = content.substring(content.length() - EVAL_TAIL_CHARS);
        log.info("[EvalAI] 본문 샘플링 original={}chars head={}+tail={}chars",
                content.length(), EVAL_HEAD_CHARS, EVAL_TAIL_CHARS);
        return head + "\n\n...(중략)...\n\n" + tail;
    }
}
