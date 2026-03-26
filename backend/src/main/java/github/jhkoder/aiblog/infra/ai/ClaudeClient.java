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
public class ClaudeClient implements AiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
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
        return callApi(prompt, textModel, apiKey, 16000, memberId);
    }

    public AiResponse generateImagePromptContent(String imagePrompt, String apiKey, Long memberId) {
        return callApi(imagePrompt, OPUS, apiKey, 1024, memberId);
    }

    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    private AiResponse callApi(String prompt, String model, String apiKey, int maxTokens, Long memberId) {
        if (apiKey == null || apiKey.isBlank()) throw new ExternalApiException("Claude API 키가 설정되지 않았습니다. 마이페이지에서 API 키를 등록해주세요.");
        String key = apiKey;

        String responseBody = null;
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();

            responseBody = webClientBuilder.build()
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
            log.error("[Claude] API 오류 — response: {}", responseBody);
            throw new ExternalApiException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Claude Streaming API.
     * Claude는 text/event-stream으로 content_block_delta 이벤트에서 delta.text를 emit한다.
     */
    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new ExternalApiException("Claude API 키가 설정되지 않았습니다."));
        }
        String textModel = (model != null && !model.isBlank()) ? model : SONNET;

        Map<String, Object> body;
        try {
            body = Map.of(
                    "model", textModel,
                    "max_tokens", 16000,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
        } catch (Exception e) {
            return Flux.error(new ExternalApiException("Claude 요청 직렬화 실패: " + e.getMessage(), e));
        }

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return Flux.error(new ExternalApiException("Claude 요청 직렬화 실패: " + e.getMessage(), e));
        }

        log.info("[Claude] streamComplete 호출 model={} promptLen={}", textModel, prompt.length());
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.info("[Claude] HTTP 연결 수립, SSE 수신 대기 중"))
                .doOnNext(line -> log.info("[Claude] RAW line=[{}]", line.replace("\n", "\\n").replace("\r", "\\r")))
                .flatMap(line -> {
                    // SSE line: "data: {...}"
                    if (!line.startsWith("data: ")) return Flux.empty();
                    String json = line.substring(6).trim();
                    if ("[DONE]".equals(json)) return Flux.empty();
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        String type = node.path("type").asText();
                        if ("content_block_delta".equals(type)) {
                            String delta = node.path("delta").path("text").asText("");
                            if (!delta.isEmpty()) return Flux.just(delta);
                        }
                    } catch (Exception e) {
                        log.warn("[Claude] SSE 라인 파싱 실패: {} line=[{}]", e.getMessage(), line);
                    }
                    return Flux.empty();
                })
                .doOnComplete(() -> log.info("[Claude] SSE 스트림 종료"))
                .onErrorMap(e -> {
                    log.error("[Claude] 스트리밍 오류: {}", e.getMessage(), e);
                    return new ExternalApiException("Claude 스트리밍 실패: " + e.getMessage(), e);
                });
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
