package github.jhkoder.aiblog.infra.image;

import github.jhkoder.aiblog.common.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiImageClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String MODEL = "gemini-2.5-flash-image";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    /** 프롬프트로 이미지 생성, base64 PNG 바이트 반환 */
    public byte[] generateImage(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                    "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                    "responseModalities", List.of("TEXT", "IMAGE")
                )
            );

            String json = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + "/v1beta/models/" + MODEL + ":generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            for (JsonNode part : parts) {
                JsonNode inline = part.path("inlineData");
                if (!inline.isMissingNode()) {
                    String b64 = inline.path("data").asText();
                    return Base64.getDecoder().decode(b64);
                }
            }
            throw new ExternalApiException("Gemini 이미지 생성 응답에 이미지가 없습니다.", null);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Gemini 이미지 생성 실패: " + e.getMessage(), e);
        }
    }
}
