package github.jhkoder.aiblog.infra.ai;

public interface AiClient {
    String complete(String prompt, String model, String apiKey);

    default AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        String text = complete(prompt, model, apiKey);
        return new AiResponse(text, 0, 0);
    }

    record AiResponse(String text, long inputTokens, long outputTokens) {}
}
