package github.jhkoder.aiblog.prompt.usecase;

import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.prompt.dto.PromptRequest;
import github.jhkoder.aiblog.prompt.dto.PromptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePromptUseCase {

    private final PromptRepository promptRepository;

    @Transactional
    public PromptResponse execute(Long memberId, PromptRequest request) {
        long existingCount = promptRepository.countByMemberId(memberId);
        Prompt prompt = Prompt.create(memberId, request.getTitle(), request.getContent(), request.isPublic(), existingCount);
        return PromptResponse.from(promptRepository.save(prompt));
    }
}
