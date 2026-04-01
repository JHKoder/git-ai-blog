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
    /** 모델별 평균 응답 시간(ms). durationMs > 0 인 row만 포함. 데이터 없으면 Optional.empty(). */
    Optional<Double> findAvgDurationMsByModel(String model);
}
