package github.jhkoder.aiblog.infra.ai;

import reactor.core.publisher.Flux;

public interface AiClient {
    String complete(String prompt, String model, String apiKey);

    default AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        String text = complete(prompt, model, apiKey);
        return new AiResponse(text, 0, 0);
    }

    /**
     * AI 응답을 토큰 단위로 스트리밍한다.
     * 각 클라이언트에서 SSE delta 청크를 파싱해 텍스트 조각만 emit.
     */
    default Flux<String> streamComplete(String prompt, String model, String apiKey) {
        return Flux.just(complete(prompt, model, apiKey));
    }

    /**
     * System Prompt + cache_control: ephemeral 분리 스트리밍.
     * Claude는 system 필드 캐싱으로 반복 요청 시 입력 토큰 절감.
     * 다른 클라이언트는 system + user를 합쳐 단일 프롬프트로 폴백.
     */
    default Flux<String> streamCompleteWithSystem(String systemPrompt, String userPrompt, String model, String apiKey) {
        String combined = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt + "\n\n" + userPrompt
                : userPrompt;
        return streamComplete(combined, model, apiKey);
    }

    record AiResponse(String text, long inputTokens, long outputTokens) {}
}
