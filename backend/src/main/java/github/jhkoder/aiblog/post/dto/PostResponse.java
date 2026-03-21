package github.jhkoder.aiblog.post.dto;

import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PostResponse {
    private Long id;
    private String title;
    private String content;
    private ContentType contentType;
    private PostStatus status;
    private String hashnodeId;
    private String hashnodeUrl;
    private List<String> tags;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .contentType(post.getContentType())
                .status(post.getStatus())
                .hashnodeId(post.getHashnodeId())
                .hashnodeUrl(post.getHashnodeUrl())
                .tags(List.copyOf(post.getTags()))
                .viewCount(post.getViewCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
