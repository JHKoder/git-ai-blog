package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrokClient implements AiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final RateLimitCache rateLimitCache;

    @Value("${ai.grok.base-url}")
    private String baseUrl;

    @Value("${ai.model.grok.default}")
    private String defaultModel;

    public String getDefaultModel() { return defaultModel; }

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return completeWithUsage(prompt, model, apiKey, null).text();
    }

    @Override
    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    public AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        if (apiKey == null || apiKey.isBlank()) throw new ExternalApiException("Grok API 키가 설정되지 않았습니다. 마이페이지에서 API 키를 등록해주세요.");
        String key = apiKey;

        String responseBody = null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model != null ? model : defaultModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();

            responseBody = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
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
                    long tokenLimit     = parseLong(headers.getFirst("x-ratelimit-limit-tokens"), -1);
                    long tokenRemaining = parseLong(headers.getFirst("x-ratelimit-remaining-tokens"), -1);
                    long reqLimit       = parseLong(headers.getFirst("x-ratelimit-limit-requests"), -1);
                    long reqRemaining   = parseLong(headers.getFirst("x-ratelimit-remaining-requests"), -1);
                    rateLimitCache.update(memberId, "grok",
                            new RateLimitCache.RateLimitInfo(tokenLimit, tokenRemaining, reqLimit, reqRemaining));
                }
            }

            JsonNode response = objectMapper.readTree(responseBody);
            String text = response.get("choices").get(0).get("message").get("content").asText();
            JsonNode usage = response.path("usage");
            return new AiResponse(text, usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));

        } catch (Exception e) {
            log.error("[Grok] API 오류 — response: {}", responseBody);
            throw new ExternalApiException("Grok API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Grok Streaming API (OpenAI 호환).
     * choices[0].delta.content 에서 텍스트를 emit한다.
     */
    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new ExternalApiException("Grok API 키가 설정되지 않았습니다."));
        }
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(Map.of(
                    "model", resolvedModel,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
        } catch (Exception e) {
            return Flux.error(new ExternalApiException("Grok 요청 직렬화 실패: " + e.getMessage(), e));
        }

        log.info("[Grok] streamComplete 호출 model={} promptLen={}", resolvedModel, prompt.length());
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.info("[Grok] HTTP 연결 수립, SSE 수신 대기 중"))
                .flatMap(line -> {
                    String json = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (json.isEmpty() || "[DONE]".equals(json)) return Flux.empty();
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                        if (!delta.isEmpty()) return Flux.just(delta);
                    } catch (Exception e) {
                        log.warn("[Grok] SSE 라인 파싱 실패: {} line=[{}]", e.getMessage(), line);
                    }
                    return Flux.empty();
                })
                .doOnComplete(() -> log.info("[Grok] SSE 스트림 종료"))
                .onErrorMap(e -> {
                    log.error("[Grok] 스트리밍 오류: {}", e.getMessage(), e);
                    return new ExternalApiException("Grok 스트리밍 실패: " + e.getMessage(), e);
                });
    }

    /** Grok API 키 유효성 검증 — /v1/models 호출 */
    public void validate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessRuleException("Grok API 키가 비어있습니다.");
        try {
            Integer statusCode = webClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .exchangeToMono(res -> Mono.just(res.statusCode().value()))
                    .block();
            if (statusCode == null || statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
                throw new BusinessRuleException("유효하지 않은 Grok API 키입니다.");
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Grok API 키 검증 실패: " + e.getMessage());
        }
    }

    private long parseLong(String val, long def) {
        if (val == null) return def;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return def; }
    }
}
