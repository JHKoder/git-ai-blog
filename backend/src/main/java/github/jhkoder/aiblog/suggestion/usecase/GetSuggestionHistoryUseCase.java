package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSuggestionHistoryUseCase {

    private final PostRepository postRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Transactional(readOnly = true)
    public List<AiSuggestionResponse> execute(Long postId, Long memberId) {
        postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        return aiSuggestionRepository.findByPostIdOrderByCreatedAtDesc(postId)
                .stream().map(AiSuggestionResponse::from).toList();
    }
}
