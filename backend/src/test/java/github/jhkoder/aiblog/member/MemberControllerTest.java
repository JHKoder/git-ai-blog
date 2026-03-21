package github.jhkoder.aiblog.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.jhkoder.aiblog.config.SecurityConfig;
import github.jhkoder.aiblog.config.TestRedisConfig;
import github.jhkoder.aiblog.member.controller.MemberController;
import github.jhkoder.aiblog.member.dto.MemberResponse;
import github.jhkoder.aiblog.member.usecase.*;
import github.jhkoder.aiblog.security.CustomOAuth2UserService;
import github.jhkoder.aiblog.security.JwtAuthenticationFilter;
import github.jhkoder.aiblog.security.JwtProvider;
import github.jhkoder.aiblog.security.OAuth2SuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MemberController @WebMvcTest.
 * Security 필터와 함께 MVC 레이어를 테스트한다.
 */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, TestRedisConfig.class})
@ActiveProfiles("local")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private GetMemberProfileUseCase getMemberProfileUseCase;

    @MockitoBean
    private ConnectHashnodeUseCase connectHashnodeUseCase;

    @MockitoBean
    private DisconnectHashnodeUseCase disconnectHashnodeUseCase;

    @MockitoBean
    private UpdateApiKeysUseCase updateApiKeysUseCase;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    private Authentication auth(Long memberId) {
        return new UsernamePasswordAuthenticationToken(memberId, null, List.of());
    }

    @Test
    @DisplayName("GET /api/members/me - 인증된 사용자는 프로필을 조회할 수 있다")
    void getMe_authenticated() throws Exception {
        Long memberId = 1L;
        MemberResponse response = MemberResponse.builder()
                .id(memberId)
                .username("testuser")
                .avatarUrl("https://avatar.url")
                .hasHashnodeConnection(false)
                .hasClaudeApiKey(false)
                .build();

        when(getMemberProfileUseCase.execute(memberId)).thenReturn(response);

        mockMvc.perform(get("/api/members/me")
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.id").value(memberId));
    }

    @Test
    @DisplayName("GET /api/members/me - 인증되지 않은 요청은 403을 반환한다")
    void getMe_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/members/hashnode-connect - Hashnode 연결 성공")
    void connectHashnode() throws Exception {
        Long memberId = 2L;
        MemberResponse response = MemberResponse.builder()
                .id(memberId)
                .username("user2")
                .hasHashnodeConnection(true)
                .build();

        when(connectHashnodeUseCase.execute(eq(memberId), any())).thenReturn(response);

        Map<String, String> body = Map.of("token", "hn-token-123", "publicationId", "pub-id-456");

        mockMvc.perform(post("/api/members/hashnode-connect")
                        .with(authentication(auth(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasHashnodeConnection").value(true));
    }

    @Test
    @DisplayName("POST /api/members/hashnode-connect - 빈 token은 400을 반환한다")
    void connectHashnode_validation() throws Exception {
        Map<String, String> body = Map.of("token", "", "publicationId", "pub-id");

        mockMvc.perform(post("/api/members/hashnode-connect")
                        .with(authentication(auth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/members/hashnode-connect - Hashnode 연결 해제")
    void disconnectHashnode() throws Exception {
        Long memberId = 3L;

        mockMvc.perform(delete("/api/members/hashnode-connect")
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/members/api-keys - API 키 업데이트")
    void updateApiKeys() throws Exception {
        Long memberId = 4L;
        MemberResponse response = MemberResponse.builder()
                .id(memberId)
                .username("user4")
                .hasClaudeApiKey(true)
                .hasGrokApiKey(true)
                .build();

        when(updateApiKeysUseCase.execute(eq(memberId), any())).thenReturn(response);

        Map<String, String> body = Map.of("claudeApiKey", "sk-ant-123", "grokApiKey", "xai-456");

        mockMvc.perform(patch("/api/members/api-keys")
                        .with(authentication(auth(memberId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasClaudeApiKey").value(true));
    }
}
