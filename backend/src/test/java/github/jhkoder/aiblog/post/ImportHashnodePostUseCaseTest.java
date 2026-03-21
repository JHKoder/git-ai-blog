package github.jhkoder.aiblog.post;

import github.jhkoder.aiblog.common.exception.NotFoundException;
import github.jhkoder.aiblog.infra.hashnode.HashnodeClient;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.post.dto.PostResponse;
import github.jhkoder.aiblog.post.usecase.ImportHashnodePostUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Hashnode 연동 후 게시글 불러오기 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ImportHashnodePostUseCaseTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private HashnodeClient hashnodeClient;

    @InjectMocks
    private ImportHashnodePostUseCase useCase;

    @Test
    @DisplayName("Hashnode에서 게시글 2건을 불러오면 DB에 저장 후 결과를 반환한다")
    void execute_fetchesAndSavesHashnodePosts() {
        Long memberId = 1L;
        Member member = Member.create("github-123", "testuser", "https://avatar.url");
        member.connectHashnode("hn-token-123", "pub-id-456");

        List<HashnodeClient.HashnodePostInfo> hnPosts = List.of(
                new HashnodeClient.HashnodePostInfo("hn-1", "첫 번째 글", "내용1", "https://hashnode.com/post/1"),
                new HashnodeClient.HashnodePostInfo("hn-2", "두 번째 글", "내용2", "https://hashnode.com/post/2")
        );

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(hashnodeClient.fetchMyPosts(member.getHashnodeToken(), member.getHashnodePublicationId()))
                .thenReturn(hnPosts);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<PostResponse> result = useCase.execute(memberId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("첫 번째 글");
        assertThat(result.get(0).getHashnodeId()).isEqualTo("hn-1");
        assertThat(result.get(0).getHashnodeUrl()).isEqualTo("https://hashnode.com/post/1");
        assertThat(result.get(1).getTitle()).isEqualTo("두 번째 글");
    }

    @Test
    @DisplayName("Hashnode에 게시글이 없으면 빈 리스트를 반환한다")
    void execute_noHashnodePosts_returnsEmpty() {
        Long memberId = 2L;
        Member member = Member.create("github-456", "user2", "https://avatar2.url");
        member.connectHashnode("hn-token-456", "pub-id-789");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(hashnodeClient.fetchMyPosts(any(), any())).thenReturn(List.of());

        List<PostResponse> result = useCase.execute(memberId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 NotFoundException을 던진다")
    void execute_memberNotFound_throwsNotFoundException() {
        Long memberId = 99L;
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(memberId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("회원을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("token과 publicationId를 직접 전달해서 게시글을 불러올 수 있다")
    void execute_withExplicitTokenAndPublicationId_fetchesPosts() {
        Long memberId = 3L;
        String token = "direct-token";
        String publicationId = "direct-pub-id";

        HashnodeClient.HashnodePostInfo hnPost =
                new HashnodeClient.HashnodePostInfo("hn-10", "직접 불러오기", "내용", "https://hashnode.com/post/10");

        when(hashnodeClient.fetchMyPosts(token, publicationId)).thenReturn(List.of(hnPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<PostResponse> result = useCase.execute(memberId, token, publicationId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("직접 불러오기");
        assertThat(result.get(0).getHashnodeId()).isEqualTo("hn-10");
    }

    @Test
    @DisplayName("불러온 게시글은 PUBLISHED 상태이다")
    void execute_importedPosts_arePublished() {
        Long memberId = 4L;
        Member member = Member.create("github-789", "user3", "https://avatar3.url");
        member.connectHashnode("hn-token-789", "pub-id-012");

        HashnodeClient.HashnodePostInfo hnPost =
                new HashnodeClient.HashnodePostInfo("hn-20", "발행된 글", "내용", "https://hashnode.com/post/20");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(hashnodeClient.fetchMyPosts(any(), any())).thenReturn(List.of(hnPost));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<PostResponse> result = useCase.execute(memberId);

        assertThat(result.get(0).getStatus().name()).isEqualTo("PUBLISHED");
    }
}
