package github.jhkoder.aiblog.suggestion;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * StreamAiSuggestionUseCase 단위 테스트.
 * MockAiClient (지연 없음)로 토큰 낭비 없이 SSE 파이프라인 전체를 검증한다.
 *
 * 검증 항목:
 * - 첫 번째 emit이 "__estimated__:N" 형식인지
 * - 이후 token emit이 FAKE_CONTENT 청크인지
 * - 스트리밍 완료 시 aiSuggestionRepository.save() 호출(DB 저장)이 이루어지는지
 * - DB 평균 시간이 있으면 estimated가 DB 기반으로 계산되는지
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StreamAiSuggestionUseCaseTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long POST_ID = 10L;
    private static final String FAKE_CONTENT = "AI가 개선한 블로그 글 내용입니다. ".repeat(5);

    @Mock private PostRepository postRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private AiSuggestionRepository aiSuggestionRepository;
    @Mock private AiUsageLimiter aiUsageLimiter;
    @Mock private PromptBuilder promptBuilder;
    @Mock private TokenUsageTracker tokenUsageTracker;
    @Mock private PromptRepository promptRepository;

    private StreamAiSuggestionUseCase useCase;
    private Member member;
    private Post post;

    @BeforeEach
    void setUp() {
        MockAiClient mockAiClient = new MockAiClient(FAKE_CONTENT);  // 지연 없음

        // ClaudeClient 자리에 MockAiClient 동작을 위임하는 mock 생성
        ClaudeClient claudeMock = mock(ClaudeClient.class);
        given(claudeMock.streamComplete(anyString(), anyString(), anyString()))
                .willAnswer(inv -> mockAiClient.streamComplete(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        given(claudeMock.complete(anyString(), anyString(), anyString()))
                .willAnswer(inv -> mockAiClient.complete(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        GrokClient grokMock = mock(GrokClient.class);
        GptClient gptMock = mock(GptClient.class);
        GeminiClient geminiMock = mock(GeminiClient.class);

        given(claudeMock.getSonnet()).willReturn("claude-sonnet-4-6");

        AiClientRouter router = new AiClientRouter(claudeMock, grokMock, gptMock, geminiMock);
        ReflectionTestUtils.setField(router, "haikuContentLengthThreshold", 1000);

        useCase = new StreamAiSuggestionUseCase(
                postRepository, memberRepository, aiSuggestionRepository,
                aiUsageLimiter, router, promptBuilder, tokenUsageTracker, promptRepository
        );

        member = Member.create("github-id", "testuser", "avatar");
        ReflectionTestUtils.setField(member, "claudeApiKey", "test-key");

        post = Post.create(MEMBER_ID, "테스트 포스트", "기존 내용", ContentType.CODING);
        ReflectionTestUtils.setField(post, "id", POST_ID);
    }

    @Test
    @DisplayName("첫 번째 이벤트는 '__estimated__:N' 형식이어야 한다")
    void stream_firstEventIsEstimated() {
        givenCommonMocks();

        Flux<String> result = useCase.stream(POST_ID, MEMBER_ID, new AiSuggestionRequest());

        StepVerifier.create(result.take(1))
                .assertNext(first -> {
                    assertThat(first).startsWith("__estimated__:");
                    String secStr = first.substring("__estimated__:".length());
                    assertThat(Integer.parseInt(secStr)).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("estimated 이후 토큰 청크가 emit되고 스트림이 완료된다")
    void stream_emitsTokensAfterEstimated() {
        givenCommonMocks();

        Flux<String> result = useCase.stream(POST_ID, MEMBER_ID, new AiSuggestionRequest());

        StepVerifier.create(result)
                .assertNext(first -> assertThat(first).startsWith("__estimated__:"))
                .thenConsumeWhile(token -> !token.isBlank())
                .verifyComplete();
    }

    @Test
    @DisplayName("스트리밍 완료 후 DB에 AI 제안이 저장된다")
    void stream_completeSavesSuggestionToDb() {
        givenCommonMocks();
        // saveResult() 내에서 findByIdAndMemberId 재호출
        given(postRepository.findByIdAndMemberId(anyLong(), anyLong())).willReturn(Optional.of(post));

        List<String> tokens = useCase.stream(POST_ID, MEMBER_ID, new AiSuggestionRequest())
                .collectList()
                .block();

        assertThat(tokens).isNotNull().isNotEmpty();
        assertThat(tokens.get(0)).startsWith("__estimated__:");
        verify(aiSuggestionRepository).save(any(AiSuggestion.class));
    }

    @Test
    @DisplayName("DB 평균 30,000ms 있으면 estimated가 30초로 계산된다")
    void stream_estimatedFromDbAvg() {
        given(aiSuggestionRepository.findAvgDurationMsByModel(anyString())).willReturn(30_000.0);
        given(postRepository.findByIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.of(post));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(promptBuilder.build(any(), anyString(), any())).willReturn("프롬프트");
        given(aiSuggestionRepository.save(any(AiSuggestion.class))).willAnswer(inv -> inv.getArgument(0));

        Flux<String> result = useCase.stream(POST_ID, MEMBER_ID, new AiSuggestionRequest());

        StepVerifier.create(result.take(1))
                .assertNext(first -> {
                    String secStr = first.substring("__estimated__:".length());
                    assertThat(Integer.parseInt(secStr)).isEqualTo(30);
                })
                .verifyComplete();
    }

    private void givenCommonMocks() {
        given(postRepository.findByIdAndMemberId(POST_ID, MEMBER_ID)).willReturn(Optional.of(post));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(promptBuilder.build(any(), anyString(), any())).willReturn("프롬프트");
        given(aiSuggestionRepository.findAvgDurationMsByModel(anyString())).willReturn(null);
        given(aiSuggestionRepository.save(any(AiSuggestion.class))).willAnswer(inv -> inv.getArgument(0));
    }
}
