package github.jhkoder.aiblog.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI GPT 클라이언트.
 * 지원 모델: gpt-4o-mini (가성비), gpt-4o (고성능)
 * Rate Limit 헤더: x-ratelimit-limit-tokens, x-ratelimit-remaining-tokens,
 *                  x-ratelimit-limit-requests, x-ratelimit-remaining-requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GptClient implements AiClient {

    private static final String BASE_URL = "https://api.openai.com";

    public static final String GPT_4O_MINI = "gpt-4o-mini";
    public static final String GPT_4O      = "gpt-4o";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitCache rateLimitCache;

    @Value("${ai.gpt.api-key:}")
    private String serverApiKey;

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return completeWithUsage(prompt, model, apiKey, null).text();
    }

    @Override
    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    public AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : serverApiKey;
        if (key == null || key.isBlank()) throw new ExternalApiException("GPT API 키가 설정되지 않았습니다.");

        String resolvedModel = (model != null && !model.isBlank()) ? model : GPT_4O_MINI;

        try {
            Map<String, Object> body = Map.of(
                    "model", resolvedModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + "/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(jsonBody)
                    .exchangeToMono(res -> {
                        headersRef.set(res.headers().asHttpHeaders());
                        return res.bodyToMono(String.class);
                    })
                    .block();

            // Rate Limit 헤더 파싱
            if (memberId != null) {
                HttpHeaders headers = headersRef.get();
                if (headers != null) {
                    long tokenLimit     = parseLong(headers.getFirst("x-ratelimit-limit-tokens"), -1);
                    long tokenRemaining = parseLong(headers.getFirst("x-ratelimit-remaining-tokens"), -1);
                    long reqLimit       = parseLong(headers.getFirst("x-ratelimit-limit-requests"), -1);
                    long reqRemaining   = parseLong(headers.getFirst("x-ratelimit-remaining-requests"), -1);
                    rateLimitCache.update(memberId, "gpt",
                            new RateLimitCache.RateLimitInfo(tokenLimit, tokenRemaining, reqLimit, reqRemaining));
                }
            }

            JsonNode response = objectMapper.readTree(responseBody);
            String text = response.get("choices").get(0).get("message").get("content").asText();
            JsonNode usage = response.path("usage");
            return new AiResponse(text,
                    usage.path("prompt_tokens").asLong(0),
                    usage.path("completion_tokens").asLong(0));

        } catch (Exception e) {
            throw new ExternalApiException("GPT API 호출 실패: " + e.getMessage(), e);
        }
    }

    private long parseLong(String val, long def) {
        if (val == null) return def;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return def; }
    }
}
