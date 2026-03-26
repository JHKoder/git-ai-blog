package github.jhkoder.aiblog.suggestion.dto;

import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AiSuggestionResponse {
    private Long id;
    private Long postId;
    private String suggestedContent;
    private String suggestedTitle;
    private String suggestedTags;
    private String model;
    private String extraPrompt;
    private LocalDateTime createdAt;

    public static AiSuggestionResponse from(AiSuggestion suggestion) {
        return AiSuggestionResponse.builder()
                .id(suggestion.getId())
                .postId(suggestion.getPostId())
                .suggestedContent(suggestion.getSuggestedContent())
                .suggestedTitle(suggestion.getSuggestedTitle())
                .suggestedTags(suggestion.getSuggestedTags())
                .model(suggestion.getModel())
                .extraPrompt(suggestion.getExtraPrompt())
                .createdAt(suggestion.getCreatedAt())
                .build();
    }
}
