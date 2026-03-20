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
public class PostListResponse {
    private Long id;
    private String title;
    private ContentType contentType;
    private PostStatus status;
    private List<String> tags;
    private int viewCount;
    private LocalDateTime createdAt;

    public static PostListResponse from(Post post) {
        return PostListResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .contentType(post.getContentType())
                .status(post.getStatus())
                .tags(post.getTags())
                .viewCount(post.getViewCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
