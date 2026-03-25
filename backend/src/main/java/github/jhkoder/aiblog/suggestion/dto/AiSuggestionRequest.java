package github.jhkoder.aiblog.suggestion.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AiSuggestionRequest {
    private String model;

    @Size(max = 500, message = "추가 요청사항은 500자 이하로 입력해주세요.")
    private String extraPrompt;

    private String tempContent;
    private Long promptId;
}
