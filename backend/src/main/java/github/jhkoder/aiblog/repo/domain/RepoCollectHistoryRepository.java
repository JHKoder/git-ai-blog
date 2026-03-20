package github.jhkoder.aiblog.repo.domain;

public interface RepoCollectHistoryRepository {
    boolean existsByRepoIdAndRefTypeAndRefId(Long repoId, CollectType refType, String refId);
    RepoCollectHistory save(RepoCollectHistory history);
}
