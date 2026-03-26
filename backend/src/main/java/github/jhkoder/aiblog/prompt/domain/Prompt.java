package github.jhkoder.aiblog.prompt.domain;

import github.jhkoder.aiblog.common.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "prompts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prompt {

    private static final int MAX_PROMPTS_PER_MEMBER = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 자가성장 구상용 예약 컬럼 — 200~300자 메타 프롬프트(자기 설명).
     * 향후 "평가 결과 → 프롬프트 개선" 배치에서 활용 예정. 현재 로직 없음.
     */
    @Column(columnDefinition = "TEXT")
    private String metaPrompt;

    @Column(nullable = false)
    private int usageCount;

    @Column(nullable = false)
    private boolean isPublic;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Prompt create(Long memberId, String title, String content, boolean isPublic, long existingCount) {
        if (existingCount >= MAX_PROMPTS_PER_MEMBER) {
            throw new BusinessRuleException("프롬프트는 최대 " + MAX_PROMPTS_PER_MEMBER + "개까지 등록할 수 있습니다.");
        }
        Prompt prompt = new Prompt();
        prompt.memberId = memberId;
        prompt.title = title;
        prompt.content = content;
        prompt.isPublic = isPublic;
        prompt.usageCount = 0;
        return prompt;
    }

    public void update(String title, String content, boolean isPublic) {
        this.title = title;
        this.content = content;
        this.isPublic = isPublic;
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }
}
