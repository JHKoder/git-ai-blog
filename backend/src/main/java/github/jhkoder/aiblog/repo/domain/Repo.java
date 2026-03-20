package github.jhkoder.aiblog.repo.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repoName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectType collectType;

    private Long webhookId;

    private String wikiLastSha;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Repo create(Long memberId, String owner, String repoName, CollectType collectType) {
        Repo repo = new Repo();
        repo.memberId = memberId;
        repo.owner = owner;
        repo.repoName = repoName;
        repo.collectType = collectType;
        return repo;
    }

    public void registerWebhook(Long webhookId) {
        this.webhookId = webhookId;
    }

    public void clearWebhook() {
        this.webhookId = null;
    }

    public void updateWikiSha(String sha) {
        this.wikiLastSha = sha;
    }
}
