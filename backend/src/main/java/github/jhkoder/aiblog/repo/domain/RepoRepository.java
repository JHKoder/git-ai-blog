package github.jhkoder.aiblog.repo.domain;

import java.util.List;
import java.util.Optional;

public interface RepoRepository {
    Repo save(Repo repo);
    Optional<Repo> findByIdAndMemberId(Long id, Long memberId);
    List<Repo> findByMemberId(Long memberId);
    List<Repo> findByOwnerAndRepoName(String owner, String repoName);
    void deleteRepo(Long id);
}
