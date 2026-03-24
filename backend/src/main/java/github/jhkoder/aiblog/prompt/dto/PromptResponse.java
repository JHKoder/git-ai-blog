package github.jhkoder.aiblog.prompt.dto;

import github.jhkoder.aiblog.prompt.domain.Prompt;

import java.time.LocalDateTime;

public record PromptResponse(
        Long id,
        Long memberId,
        String title,
        String content,
        int usageCount,
        boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PromptResponse from(Prompt prompt) {
        return new PromptResponse(
                prompt.getId(),
                prompt.getMemberId(),
                prompt.getTitle(),
                prompt.getContent(),
                prompt.getUsageCount(),
                prompt.isPublic(),
                prompt.getCreatedAt(),
                prompt.getUpdatedAt()
        );
    }
}
