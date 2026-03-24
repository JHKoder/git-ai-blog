package github.jhkoder.aiblog.prompt.dto;

import lombok.Getter;

@Getter
public class PromptRequest {
    private String title;
    private String content;
    private boolean isPublic;
}
