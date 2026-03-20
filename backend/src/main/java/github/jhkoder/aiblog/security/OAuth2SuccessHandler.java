package github.jhkoder.aiblog.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        Long memberId = (Long) oAuth2User.getAttributes().get("memberId");

        // Access Token (24h) — URL 파라미터로 전달
        String accessToken = jwtProvider.generateToken(memberId);

        // Refresh Token (30일) — HttpOnly 쿠키로 전달
        String refreshToken = refreshTokenService.issue(memberId, REFRESH_TTL);
        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(request.isSecure());
        refreshCookie.setPath("/api/auth");
        refreshCookie.setMaxAge((int) REFRESH_TTL.toSeconds());
        response.addCookie(refreshCookie);

        // JWT 값은 URL 파라미터로만 전달, 로그에 출력하지 않음
        response.sendRedirect(frontendUrl + "/oauth/callback?token=" + accessToken);
    }
}
