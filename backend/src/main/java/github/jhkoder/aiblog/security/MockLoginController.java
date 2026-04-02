package github.jhkoder.aiblog.security;

import github.jhkoder.aiblog.common.ApiResponse;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * local/dev 프로파일 전용 mock 로그인.
 * GitHub OAuth 없이 JWT를 발급해 프론트엔드로 리다이렉트.
 * GET /api/auth/mock-login → 프로파일별 테스트 회원 조회 또는 생성 후 토큰 발급
 */
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MockLoginController {

    private static final String LOCAL_GITHUB_ID = "local-test-user";
    private static final String LOCAL_USERNAME  = "local-dev";
    private static final String DEV_GITHUB_ID   = "dev-test-user";
    private static final String DEV_USERNAME    = "dev-user";
    private static final String MOCK_AVATAR     = "https://avatars.githubusercontent.com/u/0";

    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:local}")
    private String activeProfile;

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    @GetMapping("/mock-login")
    public ResponseEntity<ApiResponse<Map<String, String>>> mockLogin() {
        String githubId = "dev".equals(activeProfile) ? DEV_GITHUB_ID : LOCAL_GITHUB_ID;
        String username = "dev".equals(activeProfile) ? DEV_USERNAME  : LOCAL_USERNAME;
        Member member = memberRepository.findByGithubId(githubId)
                .orElseGet(() -> memberRepository.save(Member.create(githubId, username, MOCK_AVATAR)));
        String token = jwtProvider.generateToken(member.getId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("token", token)));
    }
}
