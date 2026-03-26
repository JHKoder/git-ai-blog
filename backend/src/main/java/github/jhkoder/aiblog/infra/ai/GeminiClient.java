package github.jhkoder.aiblog.infra.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private final ObjectMapper objectMapper;

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

        String responseBody = null;
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            responseBody = webClientBuilder.build()
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
            log.error("[Gemini] API 오류 — response: {}", responseBody);
            throw new ExternalApiException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini Streaming API.
     * streamGenerateContent 엔드포인트로 Server-Sent Events를 수신한다.
     * 각 이벤트는 완전한 JSON 객체이며 candidates[0].content.parts[0].text에서 텍스트를 추출한다.
     */
    @Override
    public Flux<String> streamComplete(String prompt, String model, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.error(new ExternalApiException("Gemini API 키가 설정되지 않았습니다."));
        }
        String resolvedModel = (model != null && !model.isBlank()) ? model : GEMINI_2_FLASH;

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            ));
        } catch (Exception e) {
            return Flux.error(new ExternalApiException("Gemini 요청 직렬화 실패: " + e.getMessage(), e));
        }

        log.info("[Gemini] streamComplete 호출 model={} promptLen={}", resolvedModel, prompt.length());
        return webClientBuilder.build()
                .post()
                .uri(BASE_URL + "/v1beta/models/" + resolvedModel + ":streamGenerateContent?key=" + apiKey + "&alt=sse")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> log.info("[Gemini] HTTP 연결 수립, SSE 수신 대기 중"))
                .flatMap(line -> {
                    String json = line.startsWith("data: ") ? line.substring(6).trim() : line.trim();
                    if (json.isEmpty()) return Flux.empty();
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        String text = node.path("candidates").path(0)
                                .path("content").path("parts").path(0)
                                .path("text").asText("");
                        if (!text.isEmpty()) return Flux.just(text);
                    } catch (Exception e) {
                        log.warn("[Gemini] SSE 라인 파싱 실패: {} line=[{}]", e.getMessage(), line);
                    }
                    return Flux.empty();
                })
                .doOnComplete(() -> log.info("[Gemini] SSE 스트림 종료"))
                .onErrorMap(e -> {
                    log.error("[Gemini] 스트리밍 오류: {}", e.getMessage(), e);
                    return new ExternalApiException("Gemini 스트리밍 실패: " + e.getMessage(), e);
                });
    }

    /** Gemini API 키 유효성 검증 — /v1beta/models 호출 */
    public void validate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) throw new BusinessRuleException("Gemini API 키가 비어있습니다.");
        try {
            Integer statusCode = webClientBuilder.build()
                    .get()
                    .uri(BASE_URL + "/v1beta/models?key=" + apiKey)
                    .exchangeToMono(res -> Mono.just(res.statusCode().value()))
                    .block();
            if (statusCode == null || statusCode == HttpStatus.UNAUTHORIZED.value()
                    || statusCode == HttpStatus.FORBIDDEN.value() || statusCode == HttpStatus.BAD_REQUEST.value()) {
                throw new BusinessRuleException("유효하지 않은 Gemini API 키입니다.");
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Gemini API 키 검증 실패: " + e.getMessage());
        }
    }
}
