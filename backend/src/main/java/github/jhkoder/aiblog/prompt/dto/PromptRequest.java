package github.jhkoder.aiblog.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PromptRequest {
    @NotBlank
    @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
    private String title;

    @NotBlank
    @Size(max = 2000, message = "내용은 2000자 이하로 입력해주세요.")
    private String content;

    private boolean isPublic;
}
