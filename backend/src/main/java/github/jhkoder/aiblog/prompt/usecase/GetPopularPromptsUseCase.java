package github.jhkoder.aiblog.prompt.usecase;

import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.prompt.dto.PromptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPopularPromptsUseCase {

    private static final int DEFAULT_LIMIT = 20;

    private final PromptRepository promptRepository;

    @Transactional(readOnly = true)
    public List<PromptResponse> execute() {
        return promptRepository.findPopularPublic(PageRequest.of(0, DEFAULT_LIMIT))
                .stream()
                .map(PromptResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PromptResponse> executeByMember(Long memberId) {
        return promptRepository.findPopularByMember(memberId, PageRequest.of(0, DEFAULT_LIMIT))
                .stream()
                .map(PromptResponse::from)
                .toList();
    }
}
