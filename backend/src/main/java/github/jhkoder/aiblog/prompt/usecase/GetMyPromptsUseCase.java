package github.jhkoder.aiblog.prompt.usecase;

import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.prompt.dto.PromptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMyPromptsUseCase {

    private final PromptRepository promptRepository;

    @Transactional(readOnly = true)
    public List<PromptResponse> execute(Long memberId) {
        return promptRepository.findByMemberIdOrderByUsageCountDesc(memberId)
                .stream()
                .map(PromptResponse::from)
                .toList();
    }
}
