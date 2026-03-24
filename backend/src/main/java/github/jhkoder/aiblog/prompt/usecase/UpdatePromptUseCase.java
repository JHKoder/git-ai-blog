package github.jhkoder.aiblog.prompt.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.prompt.dto.PromptRequest;
import github.jhkoder.aiblog.prompt.dto.PromptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePromptUseCase {

    private final PromptRepository promptRepository;

    @Transactional
    public PromptResponse execute(Long promptId, Long memberId, PromptRequest request) {
        Prompt prompt = promptRepository.findByIdAndMemberId(promptId, memberId)
                .orElseThrow(() -> new NotFoundException("프롬프트를 찾을 수 없습니다."));
        prompt.update(request.getTitle(), request.getContent(), request.isPublic());
        return PromptResponse.from(prompt);
    }
}
