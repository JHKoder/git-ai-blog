package github.jhkoder.aiblog.post;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.domain.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostRepository 통합 테스트.
 * H2 in-memory DB 사용, local 프로파일 기준.
 * Redis는 Mock으로 대체.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
@Transactional
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Test
    @DisplayName("게시글을 저장하고 ID로 조회할 수 있다")
    void saveAndFindById() {
        Post post = Post.create(1L, "제목", "내용", ContentType.CODING);

        Post saved = postRepository.save(post);

        Optional<Post> found = postRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("제목");
        assertThat(found.get().getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    @DisplayName("memberId와 id로 게시글을 조회할 수 있다")
    void findByIdAndMemberId() {
        Long memberId = 10L;
        Post post = postRepository.save(Post.create(memberId, "내 글", "내용", ContentType.CS));

        Optional<Post> found = postRepository.findByIdAndMemberId(post.getId(), memberId);
        assertThat(found).isPresent();

        Optional<Post> notFound = postRepository.findByIdAndMemberId(post.getId(), 99L);
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("memberId로 게시글 목록을 페이징 조회할 수 있다")
    void findByMemberId_paging() {
        Long memberId = 20L;
        postRepository.save(Post.create(memberId, "글1", "내용1", ContentType.ALGORITHM));
        postRepository.save(Post.create(memberId, "글2", "내용2", ContentType.CODING));
        postRepository.save(Post.create(memberId, "글3", "내용3", ContentType.TEST));

        PageRequest pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> page = postRepository.findByMemberId(memberId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("태그로 게시글을 필터링할 수 있다")
    void findByMemberIdAndTag() {
        Long memberId = 30L;
        Post p1 = Post.create(memberId, "글1", "내용", ContentType.CODING, List.of("java", "spring"));
        Post p2 = Post.create(memberId, "글2", "내용", ContentType.CODING, List.of("java"));
        Post p3 = Post.create(memberId, "글3", "내용", ContentType.CODING, List.of("python"));
        postRepository.save(p1);
        postRepository.save(p2);
        postRepository.save(p3);

        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> result = postRepository.findByMemberIdAndTag(memberId, "java", pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("PUBLISHED 상태이고 hashnodeId가 있는 게시글만 조회된다")
    void findPublishedByMemberId() {
        Long memberId = 40L;
        Post published = Post.create(memberId, "발행글", "내용", ContentType.DOCUMENT);
        published.markPublished("hashnode-id-1", "https://hashnode.com/1");
        postRepository.save(published);

        Post draft = Post.create(memberId, "초안", "내용", ContentType.ETC);
        postRepository.save(draft);

        List<Post> result = postRepository.findPublishedByMemberId(memberId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHashnodeId()).isEqualTo("hashnode-id-1");
    }

    @Test
    @DisplayName("hashnodeId와 memberId로 게시글을 조회할 수 있다")
    void findByHashnodeIdAndMemberId() {
        Long memberId = 50L;
        Post post = Post.create(memberId, "제목", "내용", ContentType.CODE_REVIEW);
        post.markPublished("hn-123", "https://hashnode.com/hn-123");
        postRepository.save(post);

        Optional<Post> found = postRepository.findByHashnodeIdAndMemberId("hn-123", memberId);
        assertThat(found).isPresent();
        assertThat(found.get().getHashnodeUrl()).isEqualTo("https://hashnode.com/hn-123");
    }

    @Test
    @DisplayName("게시글을 삭제하면 더 이상 조회되지 않는다")
    void deletePost() {
        Post post = postRepository.save(Post.create(1L, "삭제할 글", "내용", ContentType.CODING));
        Long id = post.getId();

        postRepository.deletePost(id);

        assertThat(postRepository.findById(id)).isEmpty();
    }
}
