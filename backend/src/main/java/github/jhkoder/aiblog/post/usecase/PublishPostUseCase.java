package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishPostUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final HashnodeClient hashnodeClient;

    @Transactional
    public PostResponse execute(Long postId, Long memberId) {
        Post post = postRepository.findByIdAndMemberId(postId, memberId)
                .orElseThrow(() -> new NotFoundException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        if (post.getHashnodeId() == null) {
            HashnodeClient.PublishResult result = hashnodeClient.publishPost(
                    post.getTitle(), post.getContent(), member.getHashnodeToken(), member.getHashnodePublicationId(),
                    post.getTags());
            post.markPublished(result.getId(), result.getUrl());
        } else {
            hashnodeClient.updatePost(
                    post.getHashnodeId(), post.getTitle(), post.getContent(), member.getHashnodeToken(),
                    post.getTags());
            post.markPublished(post.getHashnodeId(), post.getHashnodeUrl());
        }

        return PostResponse.from(post);
    }
}
