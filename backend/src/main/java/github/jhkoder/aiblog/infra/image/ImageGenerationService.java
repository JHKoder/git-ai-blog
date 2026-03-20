package github.jhkoder.aiblog.infra.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 생성한 텍스트에서 [IMAGE: 설명] 자리표시자를 찾아
 * GPT(DALL-E 3)로 이미지를 생성하고 Cloudinary에 업로드 후 실제 마크다운 이미지로 교체한다.
 * 이미지 생성은 GPT 모델 선택 시에만 활성화된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[IMAGE:\\s*(.+?)\\]");
    private static final Pattern EXISTING_IMAGE_PATTERN = Pattern.compile("!\\[.*?]\\(.*?\\)");
    private static final int MAX_IMAGES_PER_POST = 10;

    private final GptImageClient gptImageClient;
    private final CloudinaryClient cloudinaryClient;

    /**
     * content 내 [IMAGE: 설명] 자리표시자를 실제 이미지 마크다운으로 교체한다.
     * model이 GPT 계열이 아닌 경우 자리표시자를 제거만 한다.
     * 기존 이미지 + 새 이미지 합산이 MAX_IMAGES_PER_POST를 초과하면 초과분은 스킵한다.
     */
    public String resolveImagePlaceholders(String content, String model, String apiKey) {
        if (content == null || !content.contains("[IMAGE:")) {
            return content;
        }

        if (!isGptModel(model)) {
            log.info("이미지 생성 스킵: GPT 모델이 아님 (model={})", model);
            return removePlaceholders(content);
        }

        int existingCount = countExistingImages(content);
        if (existingCount >= MAX_IMAGES_PER_POST) {
            log.info("이미지 생성 스킵: 이미 {}개 이미지 존재 (최대 {}개)", existingCount, MAX_IMAGES_PER_POST);
            return removePlaceholders(content);
        }

        int remaining = MAX_IMAGES_PER_POST - existingCount;
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        int generated = 0;

        while (matcher.find()) {
            String imageDescription = matcher.group(1).trim();
            if (generated >= remaining) {
                log.info("이미지 생성 한도 초과로 자리표시자 제거: {}", imageDescription);
                matcher.appendReplacement(result, "");
                continue;
            }

            String imageMarkdown = generateAndUpload(imageDescription, apiKey);
            matcher.appendReplacement(result, Matcher.quoteReplacement(imageMarkdown));
            if (!imageMarkdown.isEmpty()) generated++;
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /** 하위 호환용 — model, apiKey 없이 호출 시 이미지 생성 없이 자리표시자 제거 */
    public String resolveImagePlaceholders(String content) {
        if (content == null || !content.contains("[IMAGE:")) {
            return content;
        }
        return removePlaceholders(content);
    }

    private String generateAndUpload(String description, String apiKey) {
        try {
            log.info("DALL-E 3 이미지 생성 요청: {}", description);
            byte[] imageBytes = gptImageClient.generateImage(description, apiKey);
            String publicId = "ai-blog/" + UUID.randomUUID();
            String url = cloudinaryClient.upload(imageBytes, publicId);
            log.info("이미지 업로드 완료: {}", url);
            return "\n![" + description + "](" + url + ")\n";
        } catch (Exception e) {
            log.warn("이미지 생성 실패 (자리표시자 제거): {} — {}", description, e.getMessage());
            return "";
        }
    }

    private boolean isGptModel(String model) {
        return model != null && model.startsWith("gpt-");
    }

    private int countExistingImages(String content) {
        Matcher m = EXISTING_IMAGE_PATTERN.matcher(content);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private String removePlaceholders(String content) {
        return PLACEHOLDER_PATTERN.matcher(content).replaceAll("");
    }
}
