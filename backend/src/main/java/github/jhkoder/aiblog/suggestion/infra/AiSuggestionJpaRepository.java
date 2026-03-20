package github.jhkoder.aiblog.suggestion.infra;

import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiSuggestionJpaRepository extends JpaRepository<AiSuggestion, Long>, AiSuggestionRepository {
    Optional<AiSuggestion> findTopByPostIdOrderByCreatedAtDesc(Long postId);
    List<AiSuggestion> findByPostIdOrderByCreatedAtDesc(Long postId);
    void deleteByPostId(Long postId);
}
