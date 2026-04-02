package github.jhkoder.aiblog.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * OAuth2SuccessHandler 단위 테스트.
 * - 리다이렉트 URL이 ?token= 쿼리스트링 형식인지 검증한다 (#fragment 금지)
 * - Refresh Token이 HttpOnly 쿠키로 설정되는지 검증한다
 */
@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:5173";
    private static final Long MEMBER_ID = 1L;
    private static final String ACCESS_TOKEN = "test.access.token";
    private static final String REFRESH_TOKEN = "test-refresh-uuid";

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(jwtProvider, refreshTokenService);
        ReflectionTestUtils.setField(handler, "frontendUrl", FRONTEND_URL);
    }

    @Test
    @DisplayName("인증 성공 시 ?token= 쿼리스트링으로 리다이렉트한다")
    void onAuthenticationSuccess_query_string_형식으로_리다이렉트한다() throws Exception {
        givenAuthSuccess();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String location = response.getRedirectedUrl();
        assertThat(location)
                .isEqualTo(FRONTEND_URL + "/oauth/callback?token=" + ACCESS_TOKEN);
    }

    @Test
    @DisplayName("인증 성공 시 hash fragment(#)가 아닌 query string(?)을 사용한다")
    void onAuthenticationSuccess_hash_fragment를_사용하지_않는다() throws Exception {
        givenAuthSuccess();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String location = response.getRedirectedUrl();
        assertThat(location)
                .contains("?token=")
                .doesNotContain("#token=")
                .doesNotContain("#");
    }

    @Test
    @DisplayName("인증 성공 시 Refresh Token을 HttpOnly 쿠키로 설정한다")
    void onAuthenticationSuccess_refresh_token을_HttpOnly_쿠키로_설정한다() throws Exception {
        givenAuthSuccess();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getValue()).isEqualTo(REFRESH_TOKEN);
        assertThat(refreshCookie.getPath()).isEqualTo("/api/auth");
    }

    @Test
    @DisplayName("Refresh Token 발급 시 30일 TTL로 저장한다")
    void onAuthenticationSuccess_refresh_token을_30일_TTL로_발급한다() throws Exception {
        givenAuthSuccess();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(refreshTokenService).issue(eq(MEMBER_ID), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofDays(30));
    }

    private void givenAuthSuccess() {
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(oAuth2User.getAttributes()).willReturn(Map.of("memberId", MEMBER_ID));
        given(jwtProvider.generateToken(MEMBER_ID)).willReturn(ACCESS_TOKEN);
        given(refreshTokenService.issue(eq(MEMBER_ID), any(Duration.class))).willReturn(REFRESH_TOKEN);
    }
}
