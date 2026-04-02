package github.jhkoder.aiblog.suggestion.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 평가 결과 이력.
 * EvaluateAiSuggestionUseCase 스트리밍 완료 후 저장된다.
 */
@Entity
@Table(name = "ai_evaluations", indexes = {
        @Index(name = "idx_ai_evaluations_post_id", columnList = "postId"),
        @Index(name = "idx_ai_evaluations_member_created", columnList = "memberId, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evaluationContent;

    @Column(nullable = false)
    private String model;

    @Column
    private Long durationMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static AiEvaluation create(Long postId, Long memberId, String evaluationContent,
                                      String model, Long durationMs) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.postId = postId;
        evaluation.memberId = memberId;
        evaluation.evaluationContent = evaluationContent;
        evaluation.model = model;
        evaluation.durationMs = durationMs;
        return evaluation;
    }
}
