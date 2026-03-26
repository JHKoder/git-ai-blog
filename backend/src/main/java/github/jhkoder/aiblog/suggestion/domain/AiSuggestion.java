package github.jhkoder.aiblog.suggestion.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String suggestedContent;

    @Column(nullable = false)
    private String model;

    @Column(columnDefinition = "TEXT")
    private String extraPrompt;

    /** AI 응답 소요 시간(ms). 스트리밍 완료 시점에 기록. 이전 데이터는 null. */
    @Column
    private Long durationMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static AiSuggestion create(Long postId, Long memberId, String suggestedContent,
                                      String model, String extraPrompt) {
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.postId = postId;
        suggestion.memberId = memberId;
        suggestion.suggestedContent = suggestedContent;
        suggestion.model = model;
        suggestion.extraPrompt = extraPrompt;
        return suggestion;
    }

    public static AiSuggestion createWithDuration(Long postId, Long memberId, String suggestedContent,
                                                   String model, String extraPrompt, Long durationMs) {
        AiSuggestion suggestion = create(postId, memberId, suggestedContent, model, extraPrompt);
        suggestion.durationMs = durationMs;
        return suggestion;
    }
}
