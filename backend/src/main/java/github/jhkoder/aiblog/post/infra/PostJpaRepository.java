package github.jhkoder.aiblog.post.infra;

import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.domain.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostJpaRepository extends JpaRepository<Post, Long>, PostRepository {
    Optional<Post> findByIdAndMemberId(Long id, Long memberId);
    Page<Post> findByMemberId(Long memberId, Pageable pageable);
    Optional<Post> findByHashnodeIdAndMemberId(String hashnodeId, Long memberId);

    @Query("SELECT p FROM Post p WHERE p.memberId = :memberId AND p.status = 'PUBLISHED' AND p.hashnodeId IS NOT NULL")
    List<Post> findPublishedByMemberId(Long memberId);

    @Query("SELECT DISTINCT p FROM Post p JOIN p.postTags pt WHERE p.memberId = :memberId AND pt.tag = :tag")
    Page<Post> findByMemberIdAndTag(Long memberId, String tag, Pageable pageable);

    default void deletePost(Long id) {
        deleteById(id);
    }
}
