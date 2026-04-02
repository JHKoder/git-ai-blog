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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockLoginController 테스트.
 * local/dev 프로파일에서만 활성화되며, ?token= 쿼리스트링으로 리다이렉트하는지 검증한다.
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
    @DisplayName("mock-login 요청 시 /oauth/callback?token= 형식으로 리다이렉트한다")
    void mockLogin_query_string_token으로_리다이렉트한다() throws Exception {
        Member member = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.of(member));
        given(jwtProvider.generateToken(any())).willReturn("test-access-token");

        mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth/callback?token=*"));
    }

    @Test
    @DisplayName("mock-login 요청 시 hash fragment(#)가 아닌 query string(?)으로 리다이렉트한다")
    void mockLogin_hash_fragment가_아닌_query_string을_사용한다() throws Exception {
        Member member = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.of(member));
        given(jwtProvider.generateToken(any())).willReturn("test-access-token");

        String redirectUrl = mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getRedirectedUrl();

        org.assertj.core.api.Assertions.assertThat(redirectUrl)
                .contains("?token=")
                .doesNotContain("#token=");
    }

    @Test
    @DisplayName("회원이 없으면 새로 생성하고 리다이렉트한다")
    void mockLogin_회원이_없으면_신규_생성_후_리다이렉트한다() throws Exception {
        Member newMember = Member.create("local-test-user", "local-dev",
                "https://avatars.githubusercontent.com/u/0");
        given(memberRepository.findByGithubId(anyString())).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(newMember);
        given(jwtProvider.generateToken(any())).willReturn("new-access-token");

        mockMvc.perform(get("/api/auth/mock-login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth/callback?token=*"));
    }
}
