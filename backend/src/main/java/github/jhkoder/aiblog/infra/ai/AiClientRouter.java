package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.post.domain.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 클라이언트 라우터.
 * ContentType 또는 요청 모델명에 따라 적절한 AI 클라이언트로 라우팅한다.
 *
 * 지원 모델:
 * - claude-sonnet-4-6 / claude-opus-4-5 → ClaudeClient
 * - grok-3                               → GrokClient
 * - gpt-4o-mini / gpt-4o                → GptClient
 * - gemini-2.0-flash                     → GeminiClient
 */
@Component
@RequiredArgsConstructor
public class AiClientRouter {

    private final ClaudeClient claudeClient;
    private final GrokClient grokClient;
    private final GptClient gptClient;
    private final GeminiClient geminiClient;

    public record RouteResult(AiClient client, String model, String apiKey) {}

    public RouteResult route(ContentType contentType, String requestedModel, Member member) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return routeByModel(requestedModel, member);
        }

        // ContentType 기반 기본 라우팅
        return switch (contentType) {
            case ALGORITHM -> new RouteResult(
                    grokClient,
                    GrokClient.GROK_3,
                    member.getGrokApiKey()
            );
            case CODE_REVIEW -> new RouteResult(
                    claudeClient,
                    ClaudeClient.SONNET,
                    member.getClaudeApiKey()
            );
            default -> new RouteResult(
                    claudeClient,
                    ClaudeClient.SONNET,
                    member.getClaudeApiKey()
            );
        };
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
        // 기본값: Claude Sonnet
        return new RouteResult(claudeClient, model, member.getClaudeApiKey());
    }
}
