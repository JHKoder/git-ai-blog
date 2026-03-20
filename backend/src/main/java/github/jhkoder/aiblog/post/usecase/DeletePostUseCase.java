package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletePostUseCase {

    private final PostRepository postRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final HashnodeClient hashnodeClient;

    @Transactional
    public void execute(Long postId, Long memberId) {
        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));

        if (post.getHashnodeId() != null) {
            try {
                hashnodeClient.deletePost(post.getHashnodeId());
            } catch (Exception e) {
                log.warn("Hashnode 삭제 실패 (DB 삭제는 계속 진행): {}", e.getMessage());
            }
        }

        aiSuggestionRepository.deleteByPostId(postId);
        postRepository.deletePost(postId);
    }
}
