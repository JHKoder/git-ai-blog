package github.jhkoder.aiblog.suggestion.domain;

import java.util.List;
import java.util.Optional;

public interface AiSuggestionRepository {
    AiSuggestion save(AiSuggestion suggestion);
    Optional<AiSuggestion> findById(Long id);
    Optional<AiSuggestion> findTopByPostIdOrderByCreatedAtDesc(Long postId);
    List<AiSuggestion> findByPostIdOrderByCreatedAtDesc(Long postId);
    void deleteByPostId(Long postId);
    long countByPostId(Long postId);
}
