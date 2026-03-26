package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AcceptSuggestionUseCase {

    private final PostRepository postRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Transactional
    public AiSuggestionResponse execute(Long postId, Long suggestionId, Long memberId) {
        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        AiSuggestion suggestion = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new NotFoundException("AI 제안을 찾을 수 없습니다."));

        // 이 제안이 해당 게시글의 것인지 검증
        if (!suggestion.getPostId().equals(postId)) {
            throw new NotFoundException("해당 게시글의 제안이 아닙니다.");
        }

        post.accept(suggestion.getSuggestedContent(), suggestion.getSuggestedTitle());
        return AiSuggestionResponse.from(suggestion);
    }
}
