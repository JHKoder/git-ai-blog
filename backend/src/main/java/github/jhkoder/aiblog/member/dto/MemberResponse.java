package github.jhkoder.aiblog.member.dto;

import github.jhkoder.aiblog.member.domain.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberResponse {
    private Long id;
    private String username;
    private String avatarUrl;
    private boolean hasHashnodeConnection;
    private boolean hasClaudeApiKey;
    private boolean hasGrokApiKey;
    private boolean hasGptApiKey;
    private boolean hasGeminiApiKey;
    private boolean hasGithubToken;
    private Integer aiDailyLimit;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .username(member.getUsername())
                .avatarUrl(member.getAvatarUrl())
                .hasHashnodeConnection(member.hasHashnodeConnection())
                .hasClaudeApiKey(member.hasClaudeApiKey())
                .hasGrokApiKey(member.hasGrokApiKey())
                .hasGptApiKey(member.hasGptApiKey())
                .hasGeminiApiKey(member.hasGeminiApiKey())
                .hasGithubToken(member.hasGithubToken())
                .aiDailyLimit(member.getAiDailyLimit())
                .build();
    }
}
