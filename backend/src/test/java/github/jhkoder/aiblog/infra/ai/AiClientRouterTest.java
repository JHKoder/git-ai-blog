package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.post.domain.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AiClientRouter 단위 테스트.
 * ContentType과 요청 모델에 따른 AI 클라이언트 라우팅을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiClientRouterTest {

    @Mock
    private ClaudeClient claudeClient;
    @Mock
    private GrokClient grokClient;
    @Mock
    private GptClient gptClient;
    @Mock
    private GeminiClient geminiClient;

    private AiClientRouter router;
    private Member member;

    private static final String SONNET = "claude-sonnet-4-6";
    private static final String GROK_3 = "grok-3";
    private static final String GPT_4O_MINI = "gpt-4o-mini";
    private static final String GEMINI_FLASH = "gemini-2.0-flash";

    @BeforeEach
    void setUp() {
        router = new AiClientRouter(claudeClient, grokClient, gptClient, geminiClient);
        ReflectionTestUtils.setField(router, "haikuContentLengthThreshold", 1000);
        when(claudeClient.getSonnet()).thenReturn(SONNET);
        when(grokClient.getDefaultModel()).thenReturn(GROK_3);
        member = Member.create("github-id", "testuser", "avatar");
        ReflectionTestUtils.setField(member, "grokApiKey", "grok-key");
        ReflectionTestUtils.setField(member, "claudeApiKey", "claude-key");
        ReflectionTestUtils.setField(member, "gptApiKey", "gpt-key");
        ReflectionTestUtils.setField(member, "geminiApiKey", "gemini-key");
    }

    @Test
    @DisplayName("ContentType.ALGORITHM이면 GrokClient로 라우팅된다")
    void route_algorithm_usesGrokClient() {
        AiClientRouter.RouteResult result = router.route(ContentType.ALGORITHM, null, member);

        assertThat(result.client()).isEqualTo(grokClient);
        assertThat(result.model()).isEqualTo(GROK_3);
    }

    @Test
    @DisplayName("ContentType.CODING이면 ClaudeClient Sonnet으로 라우팅된다")
    void route_coding_usesClaudeSonnet() {
        AiClientRouter.RouteResult result = router.route(ContentType.CODING, null, member);

        assertThat(result.client()).isEqualTo(claudeClient);
        assertThat(result.model()).isEqualTo(SONNET);
    }

    @Test
    @DisplayName("ContentType.CODE_REVIEW이면 ClaudeClient Sonnet으로 라우팅된다")
    void route_codeReview_usesClaudeSonnet() {
        AiClientRouter.RouteResult result = router.route(ContentType.CODE_REVIEW, null, member);

        assertThat(result.client()).isEqualTo(claudeClient);
        assertThat(result.model()).isEqualTo(SONNET);
    }

    @Test
    @DisplayName("requestedModel이 gpt-4o-mini이면 GptClient로 라우팅된다")
    void route_gptModel_usesGptClient() {
        AiClientRouter.RouteResult result = router.route(ContentType.CODING, GPT_4O_MINI, member);

        assertThat(result.client()).isEqualTo(gptClient);
        assertThat(result.model()).isEqualTo(GPT_4O_MINI);
    }

    @Test
    @DisplayName("requestedModel이 gemini-2.0-flash이면 GeminiClient로 라우팅된다")
    void route_geminiModel_usesGeminiClient() {
        AiClientRouter.RouteResult result = router.route(ContentType.CODING, GEMINI_FLASH, member);

        assertThat(result.client()).isEqualTo(geminiClient);
        assertThat(result.model()).isEqualTo(GEMINI_FLASH);
    }

    @Test
    @DisplayName("requestedModel이 grok-3이면 GrokClient로 라우팅된다")
    void route_grokModel_usesGrokClient() {
        AiClientRouter.RouteResult result = router.route(ContentType.CODING, GROK_3, member);

        assertThat(result.client()).isEqualTo(grokClient);
        assertThat(result.model()).isEqualTo(GROK_3);
    }
}
