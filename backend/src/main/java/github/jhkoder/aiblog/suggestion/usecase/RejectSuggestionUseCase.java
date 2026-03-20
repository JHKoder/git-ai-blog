package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RejectSuggestionUseCase {

    private final PostRepository postRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Transactional
    public void execute(Long postId, Long suggestionId, Long memberId) {
        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        AiSuggestion suggestion = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new NotFoundException("AI 제안을 찾을 수 없습니다."));

        if (!suggestion.getPostId().equals(postId)) {
            throw new NotFoundException("해당 게시글의 제안이 아닙니다.");
        }

        post.revertFromAiSuggested();
    }
}
