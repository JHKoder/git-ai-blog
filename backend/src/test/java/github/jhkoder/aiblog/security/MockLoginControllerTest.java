package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.config.SecurityConfig;
import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockLoginController 테스트.
 * local/dev 프로파일에서만 활성화되며, JSON {data: {token}} 형식으로 응답하는지 검증한다.
 */
@WebMvcTest(MockLoginController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TestRedisConfig.class})
@ActiveProfiles("local")
class MockLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @Test
    @DisplayName("mock-login 요청 시 200 OK와 JSON token을 반환한다")
    void mockLogin_JSON_token을_반환한다() throws Exception {
        Member member = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.of(member));
        given(jwtProvider.generateToken(any())).willReturn("test-access-token");

        mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("test-access-token"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("mock-login 응답에 redirect가 없다")
    void mockLogin_redirect가_없다() throws Exception {
        Member member = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.of(member));
        given(jwtProvider.generateToken(any())).willReturn("test-access-token");

        mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @DisplayName("회원이 없으면 새로 생성하고 token을 반환한다")
    void mockLogin_회원이_없으면_신규_생성_후_token을_반환한다() throws Exception {
        Member newMember = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(newMember);
        given(jwtProvider.generateToken(any())).willReturn("new-access-token");

        mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("new-access-token"));
    }
}
