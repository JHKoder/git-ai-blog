package github.jhkoder.aiblog.post.usecase;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncHashnodePostsUseCase {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final HashnodeClient hashnodeClient;

    public record SyncResult(int added, int updated, int deleted) {}

    @Transactional
    public SyncResult execute(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));

        List<HashnodeClient.HashnodePostInfo> hashnodePosts =
                hashnodeClient.fetchMyPosts(member.getHashnodeToken(), member.getHashnodePublicationId());

        // DB에 저장된 PUBLISHED 게시글 (hashnodeId 보유) 목록
        List<Post> dbPosts = postRepository.findPublishedByMemberId(memberId);
        Map<String, Post> dbByHashnodeId = dbPosts.stream()
                .collect(Collectors.toMap(Post::getHashnodeId, p -> p));

        Set<String> hashnodeIds = hashnodePosts.stream()
                .map(HashnodeClient.HashnodePostInfo::getId)
                .collect(Collectors.toSet());

        int added = 0, updated = 0, deleted = 0;

        // 1. Hashnode에 있고 DB에 없음 → 추가
        // 2. 둘 다 있는데 제목/내용 다름 → 업데이트
        for (HashnodeClient.HashnodePostInfo info : hashnodePosts) {
            Post existing = dbByHashnodeId.get(info.getId());
            if (existing == null) {
                Post post = Post.create(memberId, info.getTitle(), info.getContent(), ContentType.ETC);
                post.markPublished(info.getId(), info.getUrl());
                postRepository.save(post);
                added++;
            } else if (!existing.isSyncedWith(info.getTitle(), info.getContent())) {
                existing.syncFromHashnode(info.getTitle(), info.getContent(), info.getUrl());
                updated++;
            }
        }

        // 3. DB에 있고 Hashnode에 없음 → DB 삭제
        for (Post dbPost : dbPosts) {
            if (!hashnodeIds.contains(dbPost.getHashnodeId())) {
                postRepository.deletePost(dbPost.getId());
                deleted++;
                log.info("Hashnode에서 삭제된 게시글 DB 제거: postId={}, hashnodeId={}", dbPost.getId(), dbPost.getHashnodeId());
            }
        }

        log.info("Hashnode 동기화 완료: memberId={}, added={}, updated={}, deleted={}", memberId, added, updated, deleted);
        return new SyncResult(added, updated, deleted);
    }
}
