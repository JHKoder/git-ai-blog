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

    /**
     * SSE 스트리밍 Flux를 반환한다. 구독 시 AI API를 호출하고 토큰을 순서대로 emit한다.
     * 스트리밍 완료 시 DB 저장 + 토큰 사용량 기록을 수행한다 (cold publisher).
     */
    @Transactional
    public Flux<String> stream(Long postId, Long memberId, AiSuggestionRequest request) {
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

        StringBuilder accumulated = new StringBuilder();
        AtomicLong startMs = new AtomicLong(System.currentTimeMillis());

        String finalEffectiveExtraPrompt = effectiveExtraPrompt;
        return route.client().streamComplete(prompt, route.model(), route.apiKey())
                .doOnNext(accumulated::append)
                .doOnComplete(() -> {
                    long durationMs = System.currentTimeMillis() - startMs.get();
                    try {
                        aiUsageLimiter.increment(memberId);
                        tokenUsageTracker.record(memberId, route.model(),
                                (long) (prompt.length() / 4), (long) (accumulated.length() / 4));

                        AiSuggestion suggestion = AiSuggestion.createWithDuration(
                                postId, memberId, accumulated.toString(),
                                route.model(), finalEffectiveExtraPrompt, durationMs);
                        aiSuggestionRepository.save(suggestion);
                        post.markAiSuggested();
                    } catch (Exception e) {
                        log.error("[StreamAI] 완료 후 저장 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e);
                    }
                })
                .doOnError(e -> log.error("[StreamAI] 스트리밍 실패 postId={} memberId={}: {}", postId, memberId, e.getMessage(), e));
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
