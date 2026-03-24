package github.jhkoder.aiblog.prompt.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.prompt.domain.Prompt;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletePromptUseCase {

    private final PromptRepository promptRepository;

    @Transactional
    public void execute(Long promptId, Long memberId) {
        Prompt prompt = promptRepository.findByIdAndMemberId(promptId, memberId)
                .orElseThrow(() -> new NotFoundException("프롬프트를 찾을 수 없습니다."));
        promptRepository.delete(prompt);
    }
}
