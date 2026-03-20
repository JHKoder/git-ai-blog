package github.jhkoder.aiblog.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini 클라이언트.
 * 지원 모델: gemini-2.0-flash
 * API: generateContent endpoint (REST)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient implements AiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    public static final String GEMINI_2_FLASH = "gemini-2.0-flash";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return completeWithUsage(prompt, model, apiKey, null).text();
    }

    @Override
    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    public AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        if (apiKey == null || apiKey.isBlank()) throw new ExternalApiException("Gemini API 키가 설정되지 않았습니다. 마이페이지에서 API 키를 등록해주세요.");
        String key = apiKey;

        String resolvedModel = (model != null && !model.isBlank()) ? model : GEMINI_2_FLASH;

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + "/v1beta/models/" + resolvedModel + ":generateContent?key=" + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseBody);
            String text = response
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            JsonNode usage = response.path("usageMetadata");
            long inputTokens  = usage.path("promptTokenCount").asLong(0);
            long outputTokens = usage.path("candidatesTokenCount").asLong(0);

            return new AiResponse(text, inputTokens, outputTokens);

        } catch (Exception e) {
            throw new ExternalApiException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
    }
}
