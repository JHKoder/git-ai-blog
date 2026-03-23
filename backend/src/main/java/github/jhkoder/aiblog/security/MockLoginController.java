package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;

/**
 * local 프로파일 전용 mock 로그인.
 * GitHub OAuth 없이 JWT를 발급해 프론트엔드로 리다이렉트.
 * GET /api/auth/mock-login → DB 첫 번째 회원 또는 테스트 회원 생성 후 토큰 발급
 */
@Profile("local")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MockLoginController {

    private static final String MOCK_GITHUB_ID = "local-test-user";
    private static final String MOCK_USERNAME  = "local-dev";
    private static final String MOCK_AVATAR    = "https://avatars.githubusercontent.com/u/0";

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    @Value("${frontend.url}")
    private String frontendUrl;

    @GetMapping("/mock-login")
    public void mockLogin(HttpServletResponse response) throws IOException {
        Optional<Member> existing = memberRepository.findByGithubId(MOCK_GITHUB_ID);
        Member member = existing.orElseGet(() ->
                memberRepository.save(Member.create(MOCK_GITHUB_ID, MOCK_USERNAME, MOCK_AVATAR))
        );
        String token = jwtProvider.generateToken(member.getId());
        response.sendRedirect(frontendUrl + "/oauth/callback?token=" + token);
    }
}
