package github.jhkoder.aiblog.suggestion.infra;

import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiSuggestionJpaRepository extends JpaRepository<AiSuggestion, Long>, AiSuggestionRepository {
    Optional<AiSuggestion> findTopByPostIdOrderByCreatedAtDesc(Long postId);
    List<AiSuggestion> findByPostIdOrderByCreatedAtDesc(Long postId);

    @Transactional
    @Modifying
    @Query("DELETE FROM AiSuggestion a WHERE a.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);

    @Query("SELECT AVG(a.durationMs) FROM AiSuggestion a WHERE a.model = :model AND a.durationMs IS NOT NULL AND a.durationMs > 0")
    Optional<Double> findAvgDurationMsByModel(@Param("model") String model);
}
