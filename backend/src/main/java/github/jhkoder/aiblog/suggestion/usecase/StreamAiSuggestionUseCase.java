package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.AiClientRouter;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.TokenUsageTracker;
import github.jhkoder.aiblog.infra.ai.prompt.PromptBuilder;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.domain.PostStatus;
import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 개선 요청을 SSE 스트리밍으로 처리한다.
 * - 토큰 단위로 클라이언트에 실시간 emit.
 * - 스트리밍 완료 후 DB에 전체 텍스트 저장 + durationMs 기록.
 * - 토큰 사용량은 입력 토큰만 추정 (출력은 스트리밍 중 미제공).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamAiSuggestionUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiClientRouter aiClientRouter;
    private final PromptBuilder promptBuilder;
    private final TokenUsageTracker tokenUsageTracker;
    private final PromptRepository promptRepository;

    /** 모델별 예상 시간 fallback (초). durationMs 데이터 없을 때 사용 */
    private static final java.util.Map<String, Long> FALLBACK_SECONDS = java.util.Map.of(
            "claude-sonnet-4-6", 40L,
            "claude-opus-4-5",   60L,
            "grok-3",            20L,
            "gpt-4o",            30L,
            "gpt-4o-mini",       20L,
            "gemini-2.0-flash",  15L
    );

    /**
     * SSE 스트리밍 Flux를 반환한다. 구독 시 AI API를 호출하고 토큰을 순서대로 emit한다.
     * 첫 번째 이벤트로 "__estimated__:{초}" 를 emit — 프론트에서 카운트다운에 활용.
     * 스트리밍 완료 시 DB 저장 + 토큰 사용량 기록을 수행한다 (cold publisher).
     *
     * 주의: doOnComplete 는 리액터 스레드에서 실행되므로 @Transactional 이 적용되지 않는다.
     * 완료 콜백은 별도 @Transactional 메서드(saveResult)로 위임한다.
     */
    @Transactional(readOnly = true)
    public Flux<String> stream(Long postId, Long memberId, AiSuggestionRequest request) {
        log.info("[StreamAI][1] 요청 수신 postId={} memberId={} model={}", postId, memberId, request.getModel());
        aiUsageLimiter.check(memberId);

        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        String contentToImprove = (request.getTempContent() != null && !request.getTempContent().isBlank())
                ? request.getTempContent()
                : post.getContent();

        String effectiveExtraPrompt = resolveExtraPrompt(request);

        String prompt = promptBuilder.build(post.getContentType(), contentToImprove, effectiveExtraPrompt);
        AiClientRouter.RouteResult route = aiClientRouter.route(post.getContentType(), request.getModel(), member);
        log.info("[StreamAI][2] 라우팅 완료 model={} promptLen={}", route.model(), prompt.length());

        // 예상 시간 계산: DB 평균 → 없으면 모델별 fallback
        long estimatedSec = resolveEstimatedSeconds(route.model());
        log.info("[StreamAI][3] estimatedSec={}", estimatedSec);

        StringBuilder accumulated = new StringBuilder();
        AtomicLong startMs = new AtomicLong(System.currentTimeMillis());
        AtomicLong tokenCount = new AtomicLong(0);

        String finalEffectiveExtraPrompt = effectiveExtraPrompt;
        Flux<String> estimatedEvent = Flux.just("__estimated__:" + estimatedSec);
        Flux<String> tokenStream = route.client().streamComplete(prompt, route.model(), route.apiKey())
                .doOnSubscribe(s -> log.info("[StreamAI][4] AI API 구독 시작 (streamComplete 호출)"))
                .doOnNext(token -> {
                    accumulated.append(token);
                    long cnt = tokenCount.incrementAndGet();
                    log.info("[StreamAI][5] 토큰#{} [{}]", cnt, token.replace("\n", "\\n"));
                })
                .doOnComplete(() -> log.info("[StreamAI][6] 토큰 스트림 완료 총 {}개, {}자", tokenCount.get(), accumulated.length()))
                .doOnError(e -> log.error("[StreamAI][ERR] 스트리밍 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e));

        // DB 저장 완료 후 __done__ 신호 emit — 프론트가 done을 받는 시점에 DB에 데이터가 존재함을 보장
        Flux<String> saveAndDone = Flux.defer(() -> {
            long durationMs = System.currentTimeMillis() - startMs.get();
            log.info("[StreamAI][7] DB 저장 시작 durationMs={} textLen={}", durationMs, accumulated.length());
            try {
                saveResult(postId, memberId, accumulated.toString(),
                        route.model(), finalEffectiveExtraPrompt, durationMs, prompt.length());
                log.info("[StreamAI][8] DB 저장 완료 → __done__ emit");
            } catch (Exception e) {
                log.error("[StreamAI][ERR] 완료 후 저장 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e);
            }
            return Flux.just("__done__");
        });

        return estimatedEvent.concatWith(tokenStream).concatWith(saveAndDone);
    }

    private long resolveEstimatedSeconds(String model) {
        try {
            Double avg = aiSuggestionRepository.findAvgDurationMsByModel(model);
            if (avg != null && avg > 0) {
                return Math.max(1L, avg.longValue() / 1000);
            }
        } catch (Exception ignored) {}
        return FALLBACK_SECONDS.getOrDefault(model, 30L);
    }

    /**
     * 스트리밍 완료 후 DB 저장 + 사용량 기록.
     * 별도 @Transactional 메서드로 분리하여 리액터 스레드에서도 트랜잭션이 적용된다.
     * post.markAiSuggested() 는 이미 AI_SUGGESTED 상태면 멱등 처리한다.
     */
    @Transactional
    public void saveResult(Long postId, Long memberId, String text, String model,
                           String extraPrompt, long durationMs, int promptLength) {
        aiUsageLimiter.increment(memberId);
        tokenUsageTracker.record(memberId, model,
                (long) (promptLength / 4), (long) (text.length() / 4));

        AiSuggestion suggestion = AiSuggestion.createWithDuration(
                postId, memberId, text, model, extraPrompt, durationMs);
        aiSuggestionRepository.save(suggestion);

        // 이미 AI_SUGGESTED 상태인 경우 상태 전이 생략 (멱등)
        postRepository.findByIdAndMemberId(postId, memberId).ifPresent(post -> {
            if (post.getStatus() != PostStatus.AI_SUGGESTED) {
                post.markAiSuggested();
            }
        });
    }

    private String resolveExtraPrompt(AiSuggestionRequest request) {
        String extraPrompt = request.getExtraPrompt();
        if (request.getPromptId() == null) return extraPrompt;

        Prompt customPrompt = promptRepository.findById(request.getPromptId())
                .orElseThrow(() -> new NotFoundException("프롬프트를 찾을 수 없습니다."));
        customPrompt.incrementUsageCount();
        String customContent = customPrompt.getContent();
        return (extraPrompt != null && !extraPrompt.isBlank())
                ? customContent + "\n" + extraPrompt
                : customContent;
    }
}
