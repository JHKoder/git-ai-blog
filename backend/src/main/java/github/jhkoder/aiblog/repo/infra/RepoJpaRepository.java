package github.jhkoder.aiblog.repo.infra;

import github.jhkoder.aiblog.repo.domain.Repo;
import github.jhkoder.aiblog.repo.domain.RepoRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepoJpaRepository extends JpaRepository<Repo, Long>, RepoRepository {
    Optional<Repo> findByIdAndMemberId(Long id, Long memberId);
    List<Repo> findByMemberId(Long memberId);
    List<Repo> findByOwnerAndRepoName(String owner, String repoName);

    default void deleteRepo(Long id) {
        deleteById(id);
    }
}
