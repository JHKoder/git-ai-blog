package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.config.SecurityConfig;
import github.jhkoder.aiblog.config.TestRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 테스트.
 * - /api/auth/refresh: Refresh Token Rotation + 새 Access Token 발급
 * - /api/auth/logout: Refresh Token blacklist 등록 + 쿠키 삭제
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TestRedisConfig.class})
@ActiveProfiles("local")
class AuthControllerTest {

    private static final String REFRESH_TOKEN_VALUE = "valid-refresh-uuid";
    private static final String NEW_REFRESH_TOKEN   = "new-refresh-uuid";
    private static final String NEW_ACCESS_TOKEN    = "new.access.token";
    private static final Long   MEMBER_ID           = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    // ────────────────────────────────────────────
    // /api/auth/refresh
    // ────────────────────────────────────────────

    @Test
    @DisplayName("유효한 Refresh Token으로 refresh 요청 시 새 Access Token을 반환한다")
    void refresh_유효한_토큰이면_새_access_token을_반환한다() throws Exception {
        given(refreshTokenService.isValid(REFRESH_TOKEN_VALUE)).willReturn(true);
        given(refreshTokenService.getMemberId(REFRESH_TOKEN_VALUE)).willReturn(MEMBER_ID);
        given(refreshTokenService.rotate(anyString(), anyLong(), any())).willReturn(NEW_REFRESH_TOKEN);
        given(jwtProvider.generateToken(MEMBER_ID)).willReturn(NEW_ACCESS_TOKEN);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value(NEW_ACCESS_TOKEN));
    }

    @Test
    @DisplayName("Refresh Token이 없으면 401 Unauthorized를 반환한다")
    void refresh_토큰이_없으면_401을_반환한다() throws Exception {
        given(refreshTokenService.isValid(null)).willReturn(false);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token이면 401 Unauthorized를 반환한다")
    void refresh_유효하지_않은_토큰이면_401을_반환한다() throws Exception {
        given(refreshTokenService.isValid(REFRESH_TOKEN_VALUE)).willReturn(false);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh 성공 시 새 Refresh Token이 HttpOnly 쿠키로 설정된다")
    void refresh_성공_시_새_refresh_token_쿠키가_설정된다() throws Exception {
        given(refreshTokenService.isValid(REFRESH_TOKEN_VALUE)).willReturn(true);
        given(refreshTokenService.getMemberId(REFRESH_TOKEN_VALUE)).willReturn(MEMBER_ID);
        given(refreshTokenService.rotate(anyString(), anyLong(), any())).willReturn(NEW_REFRESH_TOKEN);
        given(jwtProvider.generateToken(MEMBER_ID)).willReturn(NEW_ACCESS_TOKEN);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().value("refresh_token", NEW_REFRESH_TOKEN));
    }

    // ────────────────────────────────────────────
    // /api/auth/logout
    // ────────────────────────────────────────────

    @Test
    @DisplayName("logout 요청 시 200 OK를 반환한다")
    void logout_정상_요청이면_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("logout 성공 시 Refresh Token이 blacklist에 등록된다")
    void logout_성공_시_refresh_token이_blacklist에_등록된다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(refreshTokenService).invalidate(anyString(), any());
    }

    @Test
    @DisplayName("logout 성공 시 refresh_token 쿠키가 maxAge=0으로 삭제된다")
    void logout_성공_시_쿠키가_삭제된다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN_VALUE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    @DisplayName("쿠키 없이 logout 요청해도 200 OK를 반환한다")
    void logout_쿠키_없이_요청해도_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
