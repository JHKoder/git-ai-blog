package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostCreateRequest;
import github.jhkoder.aiblog.post.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePostUseCase {

    private final PostRepository postRepository;

    @Transactional
    public PostResponse execute(Long memberId, PostCreateRequest request) {
        Post post = Post.create(memberId, request.getTitle(), request.getContent(), request.getContentType(), request.getTags());
        return PostResponse.from(postRepository.save(post));
    }
}
