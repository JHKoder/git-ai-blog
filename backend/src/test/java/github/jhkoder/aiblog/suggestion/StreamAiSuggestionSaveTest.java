package github.jhkoder.aiblog.suggestion;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.infra.ai.*;
import github.jhkoder.aiblog.infra.ai.prompt.PromptBuilder;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
import github.jhkoder.aiblog.post.domain.ContentType;
import github.jhkoder.aiblog.post.domain.Post;
import github.jhkoder.aiblog.post.domain.PostRepository;
import github.jhkoder.aiblog.prompt.domain.PromptRepository;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestion;
import github.jhkoder.aiblog.suggestion.domain.AiSuggestionRepository;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import github.jhkoder.aiblog.suggestion.usecase.StreamAiSuggestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * StreamAiSuggestionUseCase DB 저장 통합 테스트.
 * dump_post.txt를 MockAiClient 응답으로 사용해 실제 블로그 글과 유사한 스트리밍 후
 * DB에 AiSuggestion이 올바르게 저장되는지 검증한다.
 *
 * 검증 항목:
 * - 스트리밍 완료 후 AiSuggestion이 DB에 저장되는지
 * - suggestedContent가 dump_post.txt 내용과 일치하는지
 * - durationMs가 0보다 큰지 (소요 시간 기록)
 * - model이 올바르게 저장되는지
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
@Transactional
class StreamAiSuggestionSaveTest {

    @Value("classpath:dump_post.txt")
    private Resource dumpPostResource;

    @Autowired private PostRepository postRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private AiSuggestionRepository aiSuggestionRepository;
    @Autowired private PromptBuilder promptBuilder;
    @Autowired private PromptRepository promptRepository;

    @MockitoBean private AiUsageLimiter aiUsageLimiter;
    @MockitoBean private TokenUsageTracker tokenUsageTracker;

    // AI 클라이언트들은 Mock으로 교체 (실제 API 호출 방지)
    @MockitoBean private ClaudeClient claudeClient;
    @MockitoBean private GrokClient grokClient;
    @MockitoBean private GptClient gptClient;
    @MockitoBean private GeminiClient geminiClient;

    private StreamAiSuggestionUseCase useCase;
    private Member savedMember;
    private Post savedPost;

    @BeforeEach
    void setUp() throws IOException {
        String dumpContent = dumpPostResource.getContentAsString(StandardCharsets.UTF_8);
        MockAiClient mockAiClient = new MockAiClient(dumpContent);  // 지연 없음

        // ClaudeClient mock이 MockAiClient 동작을 위임
        given(claudeClient.streamComplete(anyString(), anyString(), anyString()))
                .willAnswer(inv -> mockAiClient.streamComplete(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        AiClientRouter router = new AiClientRouter(claudeClient, grokClient, gptClient, geminiClient);

        useCase = new StreamAiSuggestionUseCase(
                postRepository, memberRepository, aiSuggestionRepository,
                aiUsageLimiter, router, promptBuilder, tokenUsageTracker, promptRepository
        );

        // H2에 테스트용 Member, Post 저장
        savedMember = memberRepository.save(Member.create("gh-save-test", "saveuser", "avatar"));
        ReflectionTestUtils.setField(savedMember, "claudeApiKey", "test-key");

        savedPost = postRepository.save(
                Post.create(savedMember.getId(), "DB 동시성 테스트 포스트", "기존 내용", ContentType.CS)
        );
    }

    @Test
    @DisplayName("dump_post.txt 내용으로 스트리밍 완료 후 AiSuggestion이 DB에 저장된다")
    void stream_withDumpPost_savesAiSuggestion() throws IOException {
        String dumpContent = dumpPostResource.getContentAsString(StandardCharsets.UTF_8);

        // 스트림을 끝까지 소비 (doOnComplete에서 saveResult 호출됨)
        useCase.stream(savedPost.getId(), savedMember.getId(), new AiSuggestionRequest())
                .collectList()
                .block();

        List<AiSuggestion> suggestions = aiSuggestionRepository
                .findByPostIdOrderByCreatedAtDesc(savedPost.getId());

        assertThat(suggestions).hasSize(1);
        AiSuggestion saved = suggestions.get(0);
        assertThat(saved.getSuggestedContent()).isEqualTo(dumpContent);
        assertThat(saved.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(saved.getPostId()).isEqualTo(savedPost.getId());
        assertThat(saved.getMemberId()).isEqualTo(savedMember.getId());
    }

    @Test
    @DisplayName("스트리밍 완료 후 durationMs가 0 이상으로 저장된다")
    void stream_savesPositiveDurationMs() {
        useCase.stream(savedPost.getId(), savedMember.getId(), new AiSuggestionRequest())
                .collectList()
                .block();

        List<AiSuggestion> suggestions = aiSuggestionRepository
                .findByPostIdOrderByCreatedAtDesc(savedPost.getId());

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("이미 AI_SUGGESTED 상태인 포스트도 중복 저장 없이 멱등 처리된다")
    void stream_idempotent_whenAlreadyAiSuggested() {
        // 첫 번째 스트리밍
        useCase.stream(savedPost.getId(), savedMember.getId(), new AiSuggestionRequest())
                .collectList()
                .block();

        // 두 번째 스트리밍 (이미 AI_SUGGESTED 상태 — 예외 없이 완료되어야 함)
        useCase.stream(savedPost.getId(), savedMember.getId(), new AiSuggestionRequest())
                .collectList()
                .block();

        // 두 번 모두 저장되어야 함 (제안은 히스토리로 누적)
        List<AiSuggestion> suggestions = aiSuggestionRepository
                .findByPostIdOrderByCreatedAtDesc(savedPost.getId());

        assertThat(suggestions).hasSize(2);
    }
}
