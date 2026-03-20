package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.common.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * JWT Access Token 갱신 및 로그아웃 엔드포인트.
 * - POST /api/auth/refresh: Refresh Token 쿠키로 새 Access Token 발급 (Rotation)
 * - POST /api/auth/logout: Refresh Token blacklist 등록 + 쿠키 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final String REFRESH_COOKIE = "refresh_token";

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || !refreshTokenService.isValid(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh Token이 유효하지 않습니다."));
        }

        Long memberId = refreshTokenService.getMemberId(refreshToken);
        if (memberId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh Token에서 회원 정보를 찾을 수 없습니다."));
        }

        // Rotation: 기존 토큰 무효화 + 새 토큰 발급
        String newRefreshToken = refreshTokenService.rotate(refreshToken, memberId, REFRESH_TTL);
        String newAccessToken = jwtProvider.generateToken(memberId);

        // 새 Refresh Token 쿠키 설정
        Cookie cookie = new Cookie(REFRESH_COOKIE, newRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) REFRESH_TTL.toSeconds());
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("accessToken", newAccessToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken != null) {
            refreshTokenService.invalidate(refreshToken, REFRESH_TTL);
        }

        // 쿠키 삭제 (maxAge=0)
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.ok());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
