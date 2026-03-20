package github.jhkoder.aiblog.post.domain;

import github.jhkoder.aiblog.common.exception.InvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    private String hashnodeId;
    private String hashnodeUrl;

    @ElementCollection
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    private int viewCount;

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

    public static Post create(Long memberId, String title, String content, ContentType contentType) {
        Post post = new Post();
        post.memberId = memberId;
        post.title = title;
        post.content = content;
        post.contentType = contentType;
        post.status = PostStatus.DRAFT;
        post.viewCount = 0;
        return post;
    }

    public static Post create(Long memberId, String title, String content, ContentType contentType, List<String> tags) {
        Post post = create(memberId, title, content, contentType);
        if (tags != null) post.tags = new ArrayList<>(tags);
        return post;
    }

    public void update(String title, String content, ContentType contentType) {
        this.title = title;
        this.content = content;
        if (contentType != null) {
            this.contentType = contentType;
        }
    }

    public void updateTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void markAiSuggested() {
        if (status == PostStatus.AI_SUGGESTED) {
            throw new InvalidStateException("이미 AI 제안 상태입니다.");
        }
        this.status = PostStatus.AI_SUGGESTED;
    }

    public void accept(String suggestedContent) {
        if (status != PostStatus.AI_SUGGESTED) {
            throw new InvalidStateException("AI 제안 상태에서만 수락할 수 있습니다.");
        }
        this.content = suggestedContent;
        this.status = PostStatus.ACCEPTED;
    }

    public void markPublished(String hashnodeId, String hashnodeUrl) {
        this.hashnodeId = hashnodeId;
        this.hashnodeUrl = hashnodeUrl;
        this.status = PostStatus.PUBLISHED;
    }

    public void revertFromAiSuggested() {
        if (status != PostStatus.AI_SUGGESTED) {
            throw new InvalidStateException("AI 제안 상태에서만 되돌릴 수 있습니다.");
        }
        this.status = PostStatus.DRAFT;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isSyncedWith(String title, String content) {
        return this.title.equals(title) && this.content.equals(content);
    }

    public void syncFromHashnode(String title, String content, String hashnodeUrl) {
        this.title = title;
        this.content = content;
        this.hashnodeUrl = hashnodeUrl;
        this.status = PostStatus.PUBLISHED;
    }
}
