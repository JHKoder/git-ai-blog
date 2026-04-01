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

    @Value("${ai.model.claude.v4.sonnet}")
    private String sonnet;

    @Value("${ai.model.claude.v4.opus}")
    private String opus;

    @Value("${ai.model.claude.v4.haiku}")
    private String haiku;

    @Value("${ai.model.claude.v3.sonnet}")
    private String sonnetV3;

    @Value("${ai.model.claude.v3.haiku}")
    private String haikuV3;

    public String getSonnet()    { return sonnet; }
    public String getOpus()      { return opus; }
    public String getHaiku()     { return haiku; }
    public String getSonnetV3()  { return sonnetV3; }
    public String getHaikuV3()   { return haikuV3; }

    @Override
    public String complete(String prompt, String model, String apiKey) {
        return completeWithUsage(prompt, model, apiKey, null).text();
    }

    @Override
    public AiResponse completeWithUsage(String prompt, String model, String apiKey, Long memberId) {
        String textModel = (model != null && !model.isBlank() && !model.equals(opus)) ? model : sonnet;
        return callApi(prompt, null, textModel, apiKey, 16000, memberId);
    }

    /**
     * System Prompt + cache_control: ephemeral 을 사용하는 Claude 호출.
     * system 파라미터가 있으면 Claude API의 system 필드로 분리 전송 → 프롬프트 캐싱으로 ~90% 토큰 절감.
     */
    public AiResponse completeWithSystem(String systemPrompt, String userPrompt, String model, String apiKey, Long memberId) {
        String textModel = (model != null && !model.isBlank()) ? model : sonnet;
        return callApi(userPrompt, systemPrompt, textModel, apiKey, 16000, memberId);
    }

    public AiResponse generateImagePromptContent(String imagePrompt, String apiKey, Long memberId) {
        return callApi(imagePrompt, null, opus, apiKey, 1024, memberId);
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

    /**
     * Claude Streaming API.
     * Claude는 text/event-stream으로 content_block_delta 이벤트에서 delta.text를 emit한다.
     */
    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new ExternalApiException("Claude API 키가 설정되지 않았습니다."));
        }
        String textModel = (model != null && !model.isBlank()) ? model : sonnet;

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(Map.of(
                    "model", textModel,
                    "max_tokens", 16000,
                    "stream", true,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
        } catch (Exception e) {
            return Flux.error(new ExternalApiException("Claude 요청 직렬화 실패: " + e.getMessage(), e));
        }

        log.info("[Claude] streamComplete 호출 model={} promptLen={}", textModel, prompt.length());
        return webClientBuilder.build()
                .post()
                .uri(baseUrl + "/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "prompt-caching-2024-07-31")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.info("[Claude] HTTP 연결 수립, SSE 수신 대기 중"))
                .flatMap(line -> {
                    // bodyToFlux(String.class)는 "data: " 접두사를 제거한 JSON만 전달
                    String json = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (json.isEmpty() || "[DONE]".equals(json)) return Flux.empty();
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

    @Retry(name = "ai-client")
    @CircuitBreaker(name = "ai-client")
    private AiResponse callApi(String prompt, String systemPrompt, String model, String apiKey, int maxTokens, Long memberId) {
        if (apiKey == null || apiKey.isBlank()) throw new ExternalApiException("Claude API 키가 설정되지 않았습니다. 마이페이지에서 API 키를 등록해주세요.");

        String responseBody = null;
        try {
            Map<String, Object> body = buildRequestBody(prompt, systemPrompt, model, maxTokens);
            String jsonBody = objectMapper.writeValueAsString(body);

            AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();

            responseBody = webClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("anthropic-beta", "prompt-caching-2024-07-31")
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
     * systemPrompt 있으면 cache_control: ephemeral 적용 → 프롬프트 캐싱으로 반복 호출 시 입력 토큰 ~90% 절감.
     */
    private Map<String, Object> buildRequestBody(String prompt, String systemPrompt, String model, int maxTokens) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", List.of(Map.of(
                            "type", "text",
                            "text", systemPrompt,
                            "cache_control", Map.of("type", "ephemeral")
                    )),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );
        }
        return Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );
    }

    private long parseLong(String val, long def) {
        if (val == null) return def;
        try { return Long.parseLong(val.trim()); } catch (NumberFormatException e) { return def; }
    }
}
