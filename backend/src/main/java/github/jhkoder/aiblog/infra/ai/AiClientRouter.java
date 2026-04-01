package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.post.domain.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 클라이언트 라우터.
 * ContentType 또는 요청 모델명에 따라 적절한 AI 클라이언트로 라우팅한다.
 *
 * 지원 모델:
 * - claude-sonnet-4-6 / claude-opus-4-5 / claude-haiku-4-5-20251001 → ClaudeClient
 * - grok-3                               → GrokClient
 * - gpt-4o-mini / gpt-4o                → GptClient
 * - gemini-2.0-flash                     → GeminiClient
 *
 * Haiku 자동 라우팅 조건 (Claude 기본 라우팅 시):
 * - 모델 미지정 + content 길이 < haiku.content-length-threshold + extraPrompt 있음
 */
@Component
@RequiredArgsConstructor
public class AiClientRouter {

    private final ClaudeClient claudeClient;
    private final GrokClient grokClient;
    private final GptClient gptClient;
    private final GeminiClient geminiClient;

    @Value("${ai.haiku.content-length-threshold:1000}")
    private int haikuContentLengthThreshold;

    public record RouteResult(AiClient client, String model, String apiKey) {}

    public RouteResult route(ContentType contentType, String requestedModel, Member member) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return routeByModel(requestedModel, member);
        }

        // ContentType 기반 기본 라우팅
        return switch (contentType) {
            case ALGORITHM -> new RouteResult(
                    grokClient,
                    grokClient.getDefaultModel(),
                    member.getGrokApiKey()
            );
            default -> new RouteResult(
                    claudeClient,
                    claudeClient.getSonnet(),
                    member.getClaudeApiKey()
            );
        };
    }

    /**
     * Haiku 자동 라우팅: 모델 미지정 + Claude 기본 + content 짧음 + extraPrompt 있음 → Haiku(가성비).
     */
    public RouteResult route(ContentType contentType, String requestedModel, Member member, String content, String extraPrompt) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return routeByModel(requestedModel, member);
        }
        if (contentType != ContentType.ALGORITHM && isHaikuEligible(content, extraPrompt)) {
            return new RouteResult(claudeClient, claudeClient.getHaiku(), member.getClaudeApiKey());
        }
        return route(contentType, requestedModel, member);
    }

    private RouteResult routeByModel(String model, Member member) {
        if (model.startsWith("grok")) {
            return new RouteResult(grokClient, model, member.getGrokApiKey());
        }
        if (model.startsWith("gpt")) {
            return new RouteResult(gptClient, model, member.getGptApiKey());
        }
        if (model.startsWith("gemini")) {
            return new RouteResult(geminiClient, model, member.getGeminiApiKey());
        }
        // 기본값: Claude (모델명 그대로 사용)
        return new RouteResult(claudeClient, model, member.getClaudeApiKey());
    }

    private boolean isHaikuEligible(String content, String extraPrompt) {
        int contentLen = content != null ? content.length() : 0;
        boolean hasExtraPrompt = extraPrompt != null && !extraPrompt.isBlank();
        return contentLen < haikuContentLengthThreshold && hasExtraPrompt;
    }
}
