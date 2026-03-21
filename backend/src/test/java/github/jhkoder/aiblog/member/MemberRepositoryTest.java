package github.jhkoder.aiblog.member;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberRepository 통합 테스트.
 * H2 in-memory DB 사용, local 프로파일 기준.
 * Redis는 Mock으로 대체.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
@Transactional
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("회원을 저장하고 ID로 조회할 수 있다")
    void saveAndFindById() {
        Member member = Member.create("github-id-1", "testuser", "https://avatar.url/1");

        Member saved = memberRepository.save(member);

        Optional<Member> found = memberRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getGithubId()).isEqualTo("github-id-1");
    }

    @Test
    @DisplayName("githubId로 회원을 조회할 수 있다")
    void findByGithubId() {
        Member member = Member.create("github-id-2", "user2", "https://avatar.url/2");
        memberRepository.save(member);

        Optional<Member> found = memberRepository.findByGithubId("github-id-2");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("user2");

        Optional<Member> notFound = memberRepository.findByGithubId("non-existent");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 empty를 반환한다")
    void findById_notFound() {
        Optional<Member> found = memberRepository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 githubId로 조회하면 empty를 반환한다")
    void findByGithubId_notFound() {
        Optional<Member> notFound = memberRepository.findByGithubId("non-existent");
        assertThat(notFound).isEmpty();
    }
}
