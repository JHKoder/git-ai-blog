package github.jhkoder.aiblog.repo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repo_collect_history",
        uniqueConstraints = @UniqueConstraint(name = "uq_repo_collect_history", columnNames = {"repoId", "refType", "refId"}),
        indexes = {
                @Index(name = "idx_repo_collect_history_repo_id", columnList = "repoId")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepoCollectHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long repoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectType refType;

    /** COMMIT: SHA, PR: PR 번호(숫자 문자열) */
    @Column(nullable = false)
    private String refId;

    private Long postId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime collectedAt;

    @PrePersist
    protected void onCreate() {
        this.collectedAt = LocalDateTime.now();
    }

    public static RepoCollectHistory of(Long repoId, CollectType refType, String refId, Long postId) {
        RepoCollectHistory h = new RepoCollectHistory();
        h.repoId = repoId;
        h.refType = refType;
        h.refId = refId;
        h.postId = postId;
        return h;
    }
}
