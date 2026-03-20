package github.jhkoder.aiblog.infra.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.common.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Map;

/**
 * OpenAI DALL-E 3 이미지 생성 클라이언트.
 * GPT 모델 선택 시에만 사용한다.
 * 이미지 URL을 반환하고 Cloudinary 업로드는 호출자가 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GptImageClient {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String DALLE_MODEL = "dall-e-3";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * DALL-E 3으로 이미지를 생성하고 PNG 바이트를 반환한다.
     * URL로 이미지를 생성한 뒤 URL에서 바이트를 다운로드한다.
     */
    public byte[] generateImage(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalApiException("GPT API 키가 설정되지 않았습니다.");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", DALLE_MODEL,
                    "prompt", prompt,
                    "size", "1024x1024",
                    "quality", "standard",
                    "n", 1
            );
            String jsonBody = objectMapper.writeValueAsString(body);

            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(BASE_URL + "/v1/images/generations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseBody);
            String imageUrl = response.path("data").get(0).path("url").asText();
            log.info("DALL-E 3 이미지 생성 완료: {}", imageUrl);

            return downloadImage(imageUrl);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("GPT 이미지 생성 실패: " + e.getMessage(), e);
        }
    }

    private byte[] downloadImage(String url) {
        try {
            return webClientBuilder.build()
                    .get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            throw new ExternalApiException("이미지 다운로드 실패: " + e.getMessage(), e);
        }
    }
}
