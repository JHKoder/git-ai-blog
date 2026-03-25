package github.jhkoder.aiblog.post.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PostPageResponse(
        List<PostListResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public static PostPageResponse from(Page<PostListResponse> page) {
        return new PostPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
