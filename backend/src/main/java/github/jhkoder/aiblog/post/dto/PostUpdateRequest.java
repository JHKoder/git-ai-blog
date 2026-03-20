package github.jhkoder.aiblog.post.dto;

import github.jhkoder.aiblog.post.domain.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class PostUpdateRequest {
    @NotBlank(message = "제목은 필수입니다.")
    @Size(min = 1, max = 200, message = "제목은 1자 이상 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(min = 1, max = 100000, message = "내용은 1자 이상 100,000자 이하여야 합니다.")
    private String content;

    private ContentType contentType;

    private List<String> tags;
}
