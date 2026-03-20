package github.jhkoder.aiblog.infra.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * GitHub Webhook X-Hub-Signature-256 HMAC-SHA256 서명 검증기.
 * 서명 불일치 시 403 반환용으로 사용한다.
 */
@Slf4j
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${github.webhook.secret:local-webhook-secret}")
    private String webhookSecret;

    /**
     * payload와 X-Hub-Signature-256 헤더값을 비교해 서명을 검증한다.
     *
     * @param payload   요청 바디 원문
     * @param signature X-Hub-Signature-256 헤더값
     * @return 서명이 일치하면 true, 불일치하거나 서명이 없으면 false
     */
    public boolean verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook 서명 헤더가 없습니다.");
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), HMAC_SHA256));
            String expected = SIGNATURE_PREFIX + HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
            boolean match = expected.equals(signature);
            if (!match) {
                log.warn("Webhook 서명 불일치 — expected={}, actual={}", expected, "***");
            }
            return match;
        } catch (Exception e) {
            log.warn("Webhook 서명 검증 오류: {}", e.getMessage());
            return false;
        }
    }
}
