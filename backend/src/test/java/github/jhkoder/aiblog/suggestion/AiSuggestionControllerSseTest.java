package github.jhkoder.aiblog.suggestion;

import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.security.JwtProvider;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import github.jhkoder.aiblog.suggestion.usecase.StreamAiSuggestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AiSuggestionController SSE 통합 테스트.
 * StreamAiSuggestionUseCase를 Mock으로 교체해 SSE 이벤트 타입 순서를 검증한다.
 *
 * MockMvc로 Spring MVC SSE 응답을 전체 텍스트로 수신 후
 * SSE 라인 파싱으로 event 타입과 data를 검증한다.
 *
 * 검증 항목:
 * - POST /{postId}/stream 응답이 text/event-stream 인지
 * - estimated → token → done 이벤트 순서가 올바른지
 * - 인증 없이 요청 시 403 반환
 * - 에러 발생 시 error 이벤트 전달
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestRedisConfig.class)
@ActiveProfiles("local")
class AiSuggestionControllerSseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private StreamAiSuggestionUseCase streamAiSuggestionUseCase;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        auth = new UsernamePasswordAuthenticationToken(1L, null, List.of());
    }

    @Test
    @DisplayName("SSE 스트림에서 estimated → token × N → done 이벤트 순서가 올바르다")
    void stream_eventSequence_estimatedThenTokensThenDone() throws Exception {
        given(streamAiSuggestionUseCase.stream(anyLong(), anyLong(), any(AiSuggestionRequest.class)))
                .willReturn(Flux.concat(
                        Flux.just("__estimated__:10"),
                        Flux.just("청크1", "청크2", "청크3"),
                        Flux.just("__done__")
                ));

        MvcResult result = mockMvc.perform(
                        post("/api/ai-suggestions/1/stream")
                                .with(authentication(auth))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> eventLines = body.lines()
                .filter(line -> line.startsWith("event:"))
                .toList();

        assertThat(eventLines).containsExactly(
                "event:estimated",
                "event:token",
                "event:token",
                "event:token",
                "event:done"
        );
    }

    @Test
    @DisplayName("estimated 이벤트의 data는 초(seconds) 값이다")
    void stream_estimatedData_isSeconds() throws Exception {
        given(streamAiSuggestionUseCase.stream(anyLong(), anyLong(), any(AiSuggestionRequest.class)))
                .willReturn(Flux.just("__estimated__:10"));

        MvcResult result = mockMvc.perform(
                        post("/api/ai-suggestions/1/stream")
                                .with(authentication(auth))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> dataLines = body.lines()
                .filter(line -> line.startsWith("data:"))
                .toList();

        // estimated 이벤트의 data가 "10" (초)인지 확인
        assertThat(dataLines).anyMatch(line -> line.equals("data:10"));
    }

    @Test
    @DisplayName("인증 없이 SSE 스트림 요청 시 403을 반환한다")
    void stream_withoutAuth_returnsForbidden() throws Exception {
        mockMvc.perform(
                        post("/api/ai-suggestions/1/stream")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SSE 에러 발생 시 error 이벤트가 전달된다")
    void stream_onError_sendsErrorEvent() throws Exception {
        given(streamAiSuggestionUseCase.stream(anyLong(), anyLong(), any(AiSuggestionRequest.class)))
                .willReturn(Flux.error(new RuntimeException("AI processing failed")));

        MvcResult result = mockMvc.perform(
                        post("/api/ai-suggestions/1/stream")
                                .with(authentication(auth))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<String> eventLines = body.lines()
                .filter(line -> line.startsWith("event:"))
                .toList();

        assertThat(eventLines).contains("event:error");

        // error data에 에러 메시지가 포함되는지 확인
        List<String> dataLines = body.lines()
                .filter(line -> line.startsWith("data:"))
                .toList();
        assertThat(dataLines).anyMatch(line -> line.contains("AI processing failed"));
    }
}
