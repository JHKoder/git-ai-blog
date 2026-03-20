package github.jhkoder.aiblog.infra.image;

import github.jhkoder.aiblog.common.exception.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    /** PNG 바이트를 Cloudinary에 업로드하고 secure_url 반환 */
    public String upload(byte[] imageBytes, String publicId) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String toSign = "public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
            String signature = sha1Hex(toSign);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(imageBytes) {
                @Override public String getFilename() { return publicId + ".png"; }
            }).contentType(MediaType.IMAGE_PNG);
            builder.part("public_id", publicId);
            builder.part("timestamp", timestamp);
            builder.part("api_key", apiKey);
            builder.part("signature", signature);

            String response = webClientBuilder.build()
                    .post()
                    .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(response);
            return node.path("secure_url").asText();
        } catch (Exception e) {
            throw new ExternalApiException("Cloudinary 업로드 실패: " + e.getMessage(), e);
        }
    }

    private String sha1Hex(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(data.getBytes(StandardCharsets.UTF_8)));
    }
}
