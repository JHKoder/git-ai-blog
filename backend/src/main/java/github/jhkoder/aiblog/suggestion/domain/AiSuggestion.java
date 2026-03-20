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
}
