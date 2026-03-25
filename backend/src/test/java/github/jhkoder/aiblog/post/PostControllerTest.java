package github.jhkoder.aiblog.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.config.SecurityConfig;
import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.infra.ai.ClaudeClient;
import github.jhkoder.aiblog.infra.ai.GrokClient;
import github.jhkoder.aiblog.infra.ai.ImageUsageLimiter;
import github.jhkoder.aiblog.infra.ai.RateLimitCache;
import github.jhkoder.aiblog.infra.ai.TokenUsageTracker;
import github.jhkoder.aiblog.post.controller.PostController;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.PostStatus;
import github.jhkoder.aiblog.post.dto.PostResponse;
import github.jhkoder.aiblog.post.usecase.*;
import github.jhkoder.aiblog.security.CustomOAuth2UserService;
import github.jhkoder.aiblog.security.JwtAuthenticationFilter;
import github.jhkoder.aiblog.security.JwtProvider;
import github.jhkoder.aiblog.security.OAuth2SuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import github.jhkoder.aiblog.post.dto.PostListResponse;
import github.jhkoder.aiblog.post.dto.PostPageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PostController @WebMvcTest.
 * Security 필터와 함께 MVC 레이어를 테스트한다.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TestRedisConfig.class})
@ActiveProfiles("local")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CreatePostUseCase createPostUseCase;

    @MockitoBean
    private GetPostListUseCase getPostListUseCase;

    @MockitoBean
    private GetPostDetailUseCase getPostDetailUseCase;

    @MockitoBean
    private UpdatePostUseCase updatePostUseCase;

    @MockitoBean
    private DeletePostUseCase deletePostUseCase;

    @MockitoBean
    private PublishPostUseCase publishPostUseCase;

    @MockitoBean
    private ImportHashnodePostUseCase importHashnodePostUseCase;

    @MockitoBean
    private SyncHashnodePostsUseCase syncHashnodePostsUseCase;

    @MockitoBean
    private GenerateImageUseCase generateImageUseCase;

    @MockitoBean
    private AiUsageLimiter aiUsageLimiter;

    @MockitoBean
    private ImageUsageLimiter imageUsageLimiter;

    @MockitoBean
    private TokenUsageTracker tokenUsageTracker;

    @MockitoBean
    private RateLimitCache rateLimitCache;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    private Authentication auth(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId, null, List.of());
    }

    private PostResponse samplePost(Long id, Long memberId) {
        return PostResponse.builder()
                .id(id)
                .title("테스트 제목")
                .content("테스트 내용")
                .contentType(ContentType.CODING)
                .status(PostStatus.DRAFT)
                .tags(List.of("java"))
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/posts - 게시글 생성 성공")
    void createPost() throws Exception {
        Long memberId = 1L;
        PostResponse response = samplePost(10L, memberId);

        when(createPostUseCase.execute(eq(memberId), any())).thenReturn(response);

        Map<String, Object> body = Map.of(
                "title", "테스트 제목",
                "content", "테스트 내용",
                "contentType", "CODING",
                "tags", List.of("java")
        );

        mockMvc.perform(post("/api/posts")
                        .with(authentication(auth(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("테스트 제목"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/posts - 빈 제목은 400을 반환한다")
    void createPost_validation() throws Exception {
        Map<String, Object> body = Map.of(
                "title", "",
                "content", "내용",
                "contentType", "CODING"
        );

        mockMvc.perform(post("/api/posts")
                        .with(authentication(auth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/posts - 게시글 목록 페이징 조회")
    void listPosts() throws Exception {
        Long memberId = 2L;
        PostListResponse post = PostListResponse.builder()
                .id(1L)
                .title("테스트 제목")
                .contentType(ContentType.CODING)
                .status(PostStatus.DRAFT)
                .tags(List.of("java"))
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        PostPageResponse page = PostPageResponse.from(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        when(getPostListUseCase.execute(eq(memberId), eq(0), eq(10), eq(null))).thenReturn(page);

        mockMvc.perform(get("/api/posts")
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("테스트 제목"));
    }

    @Test
    @DisplayName("GET /api/posts/{id} - 게시글 상세 조회")
    void getPostDetail() throws Exception {
        Long memberId = 3L;
        Long postId = 20L;
        PostResponse response = samplePost(postId, memberId);

        when(getPostDetailUseCase.execute(postId, memberId)).thenReturn(response);

        mockMvc.perform(get("/api/posts/{id}", postId)
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id} - 게시글 삭제")
    void deletePost() throws Exception {
        Long memberId = 4L;
        Long postId = 30L;

        mockMvc.perform(delete("/api/posts/{id}", postId)
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인증 없이 /api/posts 접근 시 403을 반환한다")
    void listPosts_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/posts/{id} - 게시글 수정")
    void updatePost() throws Exception {
        Long memberId = 5L;
        Long postId = 40L;
        PostResponse response = samplePost(postId, memberId);

        when(updatePostUseCase.execute(eq(postId), eq(memberId), any())).thenReturn(response);

        Map<String, Object> body = Map.of(
                "title", "수정된 제목",
                "content", "수정된 내용",
                "contentType", "CS"
        );

        mockMvc.perform(put("/api/posts/{id}", postId)
                        .with(authentication(auth(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
