package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.infra.github.WebhookSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitHub Webhook 서명 검증 테스트.
 * HMAC-SHA256 서명 검증 로직을 단독으로 검증한다.
 */
class WebhookSignatureVerifierTest {

    private WebhookSignatureVerifier verifier;
    private static final String SECRET = "test-webhook-secret";

    @BeforeEach
    void setUp() {
        verifier = new WebhookSignatureVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", SECRET);
    }

    @Test
    @DisplayName("올바른 서명이면 true를 반환한다")
    void verify_validSignature_returnsTrue() throws Exception {
        String payload = "{\"action\":\"push\"}";
        String signature = computeSignature(payload, SECRET);

        assertThat(verifier.verify(payload, signature)).isTrue();
    }

    @Test
    @DisplayName("잘못된 서명이면 false를 반환한다")
    void verify_invalidSignature_returnsFalse() {
        String payload = "{\"action\":\"push\"}";
        String wrongSignature = "sha256=abc123wrongsignature";

        assertThat(verifier.verify(payload, wrongSignature)).isFalse();
    }

    @Test
    @DisplayName("서명 헤더가 null이면 false를 반환한다")
    void verify_nullSignature_returnsFalse() {
        assertThat(verifier.verify("payload", null)).isFalse();
    }

    @Test
    @DisplayName("서명 헤더가 빈 문자열이면 false를 반환한다")
    void verify_emptySignature_returnsFalse() {
        assertThat(verifier.verify("payload", "")).isFalse();
    }

    @Test
    @DisplayName("다른 시크릿으로 생성된 서명은 false를 반환한다")
    void verify_differentSecret_returnsFalse() throws Exception {
        String payload = "{\"action\":\"push\"}";
        String signatureWithDifferentSecret = computeSignature(payload, "different-secret");

        assertThat(verifier.verify(payload, signatureWithDifferentSecret)).isFalse();
    }

    @Test
    @DisplayName("페이로드가 변조된 경우 false를 반환한다")
    void verify_tamperedPayload_returnsFalse() throws Exception {
        String originalPayload = "{\"action\":\"push\"}";
        String signature = computeSignature(originalPayload, SECRET);
        String tamperedPayload = "{\"action\":\"delete\"}";

        assertThat(verifier.verify(tamperedPayload, signature)).isFalse();
    }

    private String computeSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes()));
    }
}
