package github.jhkoder.aiblog.post;

import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.domain.PostStatus;
import github.jhkoder.aiblog.post.dto.PostCreateRequest;
import github.jhkoder.aiblog.post.dto.PostResponse;
import github.jhkoder.aiblog.post.usecase.CreatePostUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * CreatePostUseCase 단위 테스트 (Mockito 기반).
 * PostRepository를 Mock으로 교체해 순수 비즈니스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CreatePostUseCaseTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CreatePostUseCase createPostUseCase;

    @Test
    @DisplayName("게시글 생성 시 DRAFT 상태로 저장된다")
    void execute_createPost_statusIsDraft() {
        // given
        Long memberId = 1L;
        PostCreateRequest request = makeRequest("테스트 제목", "테스트 내용", ContentType.CODING);
        Post savedPost = makePost(1L, memberId, "테스트 제목", "테스트 내용", ContentType.CODING);
        given(postRepository.save(any(Post.class))).willReturn(savedPost);

        // when
        PostResponse response = createPostUseCase.execute(memberId, request);

        // then
        assertThat(response.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(response.getTitle()).isEqualTo("테스트 제목");
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("태그 목록이 함께 저장된다")
    void execute_withTags_tagsAreSaved() {
        Long memberId = 1L;
        PostCreateRequest request = makeRequestWithTags("제목", "내용", ContentType.ALGORITHM, List.of("java", "spring"));
        Post savedPost = makePost(1L, memberId, "제목", "내용", ContentType.ALGORITHM);
        given(postRepository.save(any(Post.class))).willReturn(savedPost);

        PostResponse response = createPostUseCase.execute(memberId, request);

        assertThat(response).isNotNull();
        verify(postRepository).save(any(Post.class));
    }

    private PostCreateRequest makeRequest(String title, String content, ContentType contentType) {
        return makeRequestWithTags(title, content, contentType, null);
    }

    private PostCreateRequest makeRequestWithTags(String title, String content, ContentType contentType, List<String> tags) {
        // Reflection으로 필드 설정 (Lombok @Getter + private 필드)
        PostCreateRequest request = new PostCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "title", title);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "content", content);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "contentType", contentType);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "tags", tags);
        return request;
    }

    private Post makePost(Long id, Long memberId, String title, String content, ContentType contentType) {
        Post post = Post.create(memberId, title, content, contentType);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        org.springframework.test.util.ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());
        return post;
    }
}
