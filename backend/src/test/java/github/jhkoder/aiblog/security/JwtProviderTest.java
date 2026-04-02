package github.jhkoder.aiblog.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtProvider 단위 테스트.
 * 토큰 발급·검증·파싱 로직을 독립적으로 검증한다.
 */
class JwtProviderTest {

    private static final String SECRET = "test-jwt-secret-key-must-be-at-least-32-characters-long";
    private static final long EXPIRATION_MS = 86_400_000L; // 24h

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("토큰을 생성하면 null이 아닌 문자열을 반환한다")
    void generateToken_반환값이_null이_아니다() {
        String token = jwtProvider.generateToken(1L);
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("생성된 토큰에서 memberId를 정확히 추출한다")
    void getMemberId_올바른_memberId를_반환한다() {
        Long memberId = 42L;
        String token = jwtProvider.generateToken(memberId);
        assertThat(jwtProvider.getMemberId(token)).isEqualTo(memberId);
    }

    @Test
    @DisplayName("유효한 토큰은 validateToken이 true를 반환한다")
    void validateToken_유효한_토큰이면_true() {
        String token = jwtProvider.generateToken(1L);
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("위변조된 토큰은 validateToken이 false를 반환한다")
    void validateToken_위변조_토큰이면_false() {
        assertThat(jwtProvider.validateToken("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 validateToken이 false를 반환한다")
    void validateToken_만료된_토큰이면_false() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, -1L); // 즉시 만료
        String expiredToken = expiredProvider.generateToken(1L);
        assertThat(jwtProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("빈 토큰은 validateToken이 false를 반환한다")
    void validateToken_빈_문자열이면_false() {
        assertThat(jwtProvider.validateToken("")).isFalse();
    }
}
