package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostDetailUseCase {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse execute(Long postId, Long memberId) {
        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        post.incrementViewCount();
        return PostResponse.from(post);
    }
}
