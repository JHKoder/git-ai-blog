package github.jhkoder.aiblog.post.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    Post save(Post post);
    Optional<Post> findById(Long id);
    Optional<Post> findByIdAndMemberId(Long id, Long memberId);
    Page<Post> findByMemberId(Long memberId, Pageable pageable);
    Page<Post> findByMemberIdAndTag(Long memberId, String tag, Pageable pageable);
    List<Post> findPublishedByMemberId(Long memberId);
    Optional<Post> findByHashnodeIdAndMemberId(String hashnodeId, Long memberId);
    void deletePost(Long id);
}
