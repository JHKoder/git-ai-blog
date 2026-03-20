package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportHashnodePostUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final HashnodeClient hashnodeClient;

    @Transactional
    public List<PostResponse> execute(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        return doImport(memberId, member.getHashnodeToken(), member.getHashnodePublicationId());
    }

    @Transactional
    public List<PostResponse> execute(Long memberId, String token, String publicationId) {
        return doImport(memberId, token, publicationId);
    }

    private List<PostResponse> doImport(Long memberId, String token, String publicationId) {
        List<HashnodeClient.HashnodePostInfo> posts = hashnodeClient.fetchMyPosts(token, publicationId);

        return posts.stream().map(info -> {
            Post post = Post.create(memberId, info.getTitle(), info.getContent(), ContentType.ETC);
            post.markPublished(info.getId(), info.getUrl());
            return PostResponse.from(postRepository.save(post));
        }).toList();
    }
}
