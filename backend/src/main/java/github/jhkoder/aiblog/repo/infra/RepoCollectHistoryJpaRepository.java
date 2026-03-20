package github.jhkoder.aiblog.repo.infra;

import github.jhkoder.aiblog.repo.domain.CollectType;
import github.jhkoder.aiblog.repo.domain.RepoCollectHistory;
import github.jhkoder.aiblog.repo.domain.RepoCollectHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepoCollectHistoryJpaRepository
        extends JpaRepository<RepoCollectHistory, Long>, RepoCollectHistoryRepository {

    boolean existsByRepoIdAndRefTypeAndRefId(Long repoId, CollectType refType, String refId);
}
