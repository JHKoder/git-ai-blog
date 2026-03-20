package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostListUseCase {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public Page<PostListResponse> execute(Long memberId, int page, int size, String tag) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (tag != null && !tag.isBlank()) {
            return postRepository.findByMemberIdAndTag(memberId, tag, pageable)
                    .map(PostListResponse::from);
        }
        return postRepository.findByMemberId(memberId, pageable)
                .map(PostListResponse::from);
    }
}
