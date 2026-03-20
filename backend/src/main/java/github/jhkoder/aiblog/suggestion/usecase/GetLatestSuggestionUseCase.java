package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetLatestSuggestionUseCase {

    private final PostRepository postRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Transactional(readOnly = true)
    public AiSuggestionResponse execute(Long postId, Long memberId) {
        postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        return aiSuggestionRepository.findTopByPostIdOrderByCreatedAtDesc(postId)
                .map(AiSuggestionResponse::from)
                .orElseThrow(() -> new NotFoundException("AI 제안이 없습니다."));
    }
}
