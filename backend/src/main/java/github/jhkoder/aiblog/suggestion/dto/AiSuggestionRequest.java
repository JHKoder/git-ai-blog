package github.jhkoder.aiblog.suggestion.dto;

import lombok.Getter;

@Getter
public class AiSuggestionRequest {
    private String model;
    private String extraPrompt;
    private String tempContent;
}
