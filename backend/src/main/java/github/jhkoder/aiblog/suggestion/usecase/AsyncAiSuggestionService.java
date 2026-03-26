package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.AiClient;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 개선 요청을 비동기로 처리한다.
 * - 컨트롤러는 즉시 202 Accepted를 반환하고, 이 서비스에서 AI 처리 후 DB에 저장한다.
 * - 클라이언트는 /api/ai-suggestions/{postId}/latest 를 폴링해 결과를 확인한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAiSuggestionService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiClientRouter aiClientRouter;
    private final PromptBuilder promptBuilder;
    private final TokenUsageTracker tokenUsageTracker;
    private final PromptRepository promptRepository;

    @Async("aiTaskExecutor")
    @Transactional
    public void executeAsync(Long postId, Long memberId, AiSuggestionRequest request) {
        try {
            Post post = postRepository.findByIdAndMemberId(postId, memberId)
                    .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

            String contentToImprove = (request.getTempContent() != null && !request.getTempContent().isBlank())
                    ? request.getTempContent()
                    : post.getContent();

            String effectiveExtraPrompt = request.getExtraPrompt();
            if (request.getPromptId() != null) {
                Prompt customPrompt = promptRepository.findById(request.getPromptId())
                        .orElseThrow(() -> new NotFoundException("프롬프트를 찾을 수 없습니다."));
                String customContent = customPrompt.getContent();
                effectiveExtraPrompt = (effectiveExtraPrompt != null && !effectiveExtraPrompt.isBlank())
                        ? customContent + "\n" + effectiveExtraPrompt
                        : customContent;
                customPrompt.incrementUsageCount();
            }

            String prompt = promptBuilder.build(post.getContentType(), contentToImprove, effectiveExtraPrompt);
            AiClientRouter.RouteResult route = aiClientRouter.route(post.getContentType(), request.getModel(), member);

            long startMs = System.currentTimeMillis();
            AiClient.AiResponse aiResponse = route.client().completeWithUsage(prompt, route.model(), route.apiKey(), memberId);
            long durationMs = System.currentTimeMillis() - startMs;

            tokenUsageTracker.record(memberId, route.model(), aiResponse.inputTokens(), aiResponse.outputTokens());
            aiUsageLimiter.increment(memberId);

            AiSuggestion suggestion = AiSuggestion.createWithDuration(
                    postId, memberId, aiResponse.text(), route.model(), effectiveExtraPrompt, durationMs);
            aiSuggestionRepository.save(suggestion);

            post.markAiSuggested();

        } catch (Exception e) {
            log.error("[AsyncAI] postId={} memberId={} 처리 실패: {}", postId, memberId, e.getMessage(), e);
        }
    }
}
