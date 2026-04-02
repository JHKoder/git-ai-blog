package github.jhkoder.aiblog.suggestion.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    List<AiEvaluation> findByPostIdOrderByCreatedAtDesc(Long postId);

    List<AiEvaluation> findByMemberIdOrderByCreatedAtDesc(Long memberId);
}
