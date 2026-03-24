package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.ai.AiClient;
import github.jhkoder.aiblog.infra.ai.AiClientRouter;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.TokenUsageTracker;
import github.jhkoder.aiblog.infra.ai.prompt.PromptBuilder;
import github.jhkoder.aiblog.infra.image.ImageGenerationService;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestAiSuggestionUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final AiUsageLimiter aiUsageLimiter;
    private final AiClientRouter aiClientRouter;
    private final PromptBuilder promptBuilder;
    private final ImageGenerationService imageGenerationService;
    private final TokenUsageTracker tokenUsageTracker;
    private final PromptRepository promptRepository;

    @Transactional
    public AiSuggestionResponse execute(Long postId, Long memberId, AiSuggestionRequest request) {
        aiUsageLimiter.check(memberId);

        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        String contentToImprove = (request.getTempContent() != null && !request.getTempContent().isBlank())
                ? request.getTempContent()
                : post.getContent();

        // promptId가 있으면 커스텀 프롬프트 content를 extraPrompt 앞에 병합, usageCount 증가
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
        AiClient.AiResponse aiResponse = route.client().completeWithUsage(prompt, route.model(), route.apiKey(), memberId);
        String suggestedContent = aiResponse.text();

        tokenUsageTracker.record(memberId, route.model(), aiResponse.inputTokens(), aiResponse.outputTokens());
        aiUsageLimiter.increment(memberId);

        // AI가 [IMAGE: 설명] 자리표시자를 삽입하면 GPT 모델인 경우 DALL-E 3으로 이미지 생성 후 교체
        suggestedContent = imageGenerationService.resolveImagePlaceholders(suggestedContent, route.model(), route.apiKey());

        AiSuggestion suggestion = AiSuggestion.create(
                postId, memberId, suggestedContent, route.model(), effectiveExtraPrompt);
        aiSuggestionRepository.save(suggestion);

        post.markAiSuggested();

        return AiSuggestionResponse.from(suggestion);
    }
}
