package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient implements AiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitCache rateLimitCache;

    @Value("${ai.claude.base-url}")
    private String baseUrl;

    public static final String SONNET = "claude-sonnet-4-6";
    public static final String OPUS   = "claude-opus-4-5";

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return completeWithUsage(prompt, model, apiKey, null).text();
    }

    @Override
    public AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        String textModel = (model != null && !model.isBlank() && !model.equals(OPUS)) ? model : SONNET;
        return callApi(prompt, textModel, apiKey, 4096, memberId);
    }

    public AiResponse generateImagePromptContent(String imagePrompt, String apiKey, Long memberId) {
        return callApi(imagePrompt, OPUS, apiKey, 1024, memberId);
    }

    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    private AiResponse callApi(String prompt, String model, String apiKey, int maxTokens, Long memberId) {
        if (apiKey == null || apiKey.isBlank()) throw new ExternalApiException("Claude API 키가 설정되지 않았습니다. 마이페이지에서 API 키를 등록해주세요.");
        String key = apiKey;

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/messages")
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(jsonBody)
                    .exchangeToMono(res -> {
                        headersRef.set(res.headers().asHttpHeaders());
                        return res.bodyToMono(String.class);
                    })
                    .block();

            // Rate limit 헤더 파싱
            if (memberId != null) {
                HttpHeaders headers = headersRef.get();
                if (headers != null) {
                    long tokenLimit     = parseLong(headers.getFirst("anthropic-ratelimit-tokens-limit"), -1);
                    long tokenRemaining = parseLong(headers.getFirst("anthropic-ratelimit-tokens-remaining"), -1);
                    long reqLimit       = parseLong(headers.getFirst("anthropic-ratelimit-requests-limit"), -1);
                    long reqRemaining   = parseLong(headers.getFirst("anthropic-ratelimit-requests-remaining"), -1);
                    rateLimitCache.update(memberId, "claude",
                            new RateLimitCache.RateLimitInfo(tokenLimit, tokenRemaining, reqLimit, reqRemaining));
                }
            }

            JsonNode response = objectMapper.readTree(responseBody);
            String text = response.get("content").get(0).get("text").asText();
            JsonNode usage = response.path("usage");
            return new AiResponse(text, usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0));

        } catch (Exception e) {
            throw new ExternalApiException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    /** Claude API 키 유효성 검증 — /v1/models 호출 */
    public void validate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessRuleException("Claude API 키가 비어있습니다.");
        try {
            Integer statusCode = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/v1/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .exchangeToMono(res -> Mono.just(res.statusCode().value()))
                    .block();
            if (statusCode == null || statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
                throw new BusinessRuleException("유효하지 않은 Claude API 키입니다.");
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Claude API 키 검증 실패: " + e.getMessage());
        }
    }

    private long parseLong(String val, long def) {
        if (val == null) return def;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return def; }
    }
}
