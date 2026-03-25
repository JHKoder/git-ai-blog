package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostListResponse;
import github.jhkoder.aiblog.post.dto.PostPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetPostListUseCase {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public PostPageResponse execute(Long memberId, int page, int size, String tag) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (tag != null && !tag.isBlank()) {
            return PostPageResponse.from(postRepository.findByMemberIdAndTag(memberId, tag, pageable)
                    .map(PostListResponse::from));
        }
        return PostPageResponse.from(postRepository.findByMemberId(memberId, pageable)
                .map(PostListResponse::from));
    }
}
