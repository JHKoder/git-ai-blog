package github.jhkoder.aiblog.repo;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.repo.domain.CollectType;
import github.jhkoder.aiblog.repo.domain.Repo;
import github.jhkoder.aiblog.repo.domain.RepoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RepoRepository 통합 테스트.
 * H2 in-memory DB 사용, local 프로파일 기준.
 * Redis는 Mock으로 대체.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
@Transactional
class RepoRepositoryTest {

    @Autowired
    private RepoRepository repoRepository;

    @Test
    @DisplayName("레포를 저장하고 memberId로 조회할 수 있다")
    void saveAndFindByMemberId() {
        Long memberId = 1L;
        Repo repo = Repo.create(memberId, "owner1", "repo1", CollectType.COMMIT);

        Repo saved = repoRepository.save(repo);

        List<Repo> found = repoRepository.findByMemberId(memberId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getOwner()).isEqualTo("owner1");
        assertThat(found.get(0).getRepoName()).isEqualTo("repo1");
        assertThat(found.get(0).getCollectType()).isEqualTo(CollectType.COMMIT);
    }

    @Test
    @DisplayName("id와 memberId로 레포를 조회할 수 있다")
    void findByIdAndMemberId() {
        Long memberId = 10L;
        Repo repo = repoRepository.save(Repo.create(memberId, "owner2", "repo2", CollectType.PR));

        Optional<Repo> found = repoRepository.findByIdAndMemberId(repo.getId(), memberId);
        assertThat(found).isPresent();
        assertThat(found.get().getOwner()).isEqualTo("owner2");

        Optional<Repo> notFound = repoRepository.findByIdAndMemberId(repo.getId(), 99L);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("memberId로 레포 목록을 조회할 수 있다")
    void findByMemberId() {
        Long memberId = 20L;
        repoRepository.save(Repo.create(memberId, "owner3", "repoA", CollectType.WIKI));
        repoRepository.save(Repo.create(memberId, "owner3", "repoB", CollectType.README));
        repoRepository.save(Repo.create(99L, "owner4", "repoC", CollectType.COMMIT));

        List<Repo> results = repoRepository.findByMemberId(memberId);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("owner와 repoName으로 레포를 조회할 수 있다")
    void findByOwnerAndRepoName() {
        repoRepository.save(Repo.create(30L, "jhkoder", "my-repo", CollectType.COMMIT));
        repoRepository.save(Repo.create(31L, "jhkoder", "my-repo", CollectType.PR));

        List<Repo> results = repoRepository.findByOwnerAndRepoName("jhkoder", "my-repo");
        assertThat(results).hasSize(2);

        List<Repo> empty = repoRepository.findByOwnerAndRepoName("jhkoder", "other-repo");
        assertThat(empty).isEmpty();
    }

    @Test
    @DisplayName("레포를 삭제하면 더 이상 조회되지 않는다")
    void deleteRepo() {
        Long memberId = 40L;
        Repo repo = repoRepository.save(Repo.create(memberId, "owner5", "repo5", CollectType.WIKI));
        Long id = repo.getId();

        repoRepository.deleteRepo(id);

        List<Repo> remaining = repoRepository.findByMemberId(memberId);
        assertThat(remaining).isEmpty();
    }
}
