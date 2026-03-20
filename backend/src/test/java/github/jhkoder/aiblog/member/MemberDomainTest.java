package github.jhkoder.aiblog.member;

import github.jhkoder.aiblog.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Member 도메인 단위 테스트.
 * API 키 관리, Hashnode 연동 상태 등을 검증한다.
 */
class MemberDomainTest {

    @Test
    @DisplayName("Member 생성 시 githubId, username, avatarUrl이 설정된다")
    void create_setsFields() {
        Member member = Member.create("github-123", "testuser", "https://avatar.url");

        assertThat(member.getGithubId()).isEqualTo("github-123");
        assertThat(member.getUsername()).isEqualTo("testuser");
        assertThat(member.getAvatarUrl()).isEqualTo("https://avatar.url");
    }

    @Test
    @DisplayName("Hashnode 연동 전 hasHashnodeConnection은 false이다")
    void hasHashnodeConnection_beforeConnect_isFalse() {
        Member member = Member.create("id", "user", "url");
        assertThat(member.hasHashnodeConnection()).isFalse();
    }

    @Test
    @DisplayName("Hashnode 연동 후 hasHashnodeConnection은 true이다")
    void connectHashnode_setsConnection() {
        Member member = Member.create("id", "user", "url");
        member.connectHashnode("token-123", "pub-id-456");

        assertThat(member.hasHashnodeConnection()).isTrue();
    }

    @Test
    @DisplayName("Hashnode 연동 해제 후 hasHashnodeConnection은 false이다")
    void disconnectHashnode_clearsConnection() {
        Member member = Member.create("id", "user", "url");
        member.connectHashnode("token", "pub-id");
        member.disconnectHashnode();

        assertThat(member.hasHashnodeConnection()).isFalse();
    }

    @Test
    @DisplayName("Claude API 키 설정 후 hasClaudeApiKey는 true이다")
    void updateClaudeApiKey_setsKey() {
        Member member = Member.create("id", "user", "url");
        member.updateClaudeApiKey("sk-ant-123");

        assertThat(member.hasClaudeApiKey()).isTrue();
    }

    @Test
    @DisplayName("Grok API 키 설정 후 hasGrokApiKey는 true이다")
    void updateGrokApiKey_setsKey() {
        Member member = Member.create("id", "user", "url");
        member.updateGrokApiKey("xai-123");

        assertThat(member.hasGrokApiKey()).isTrue();
    }

    @Test
    @DisplayName("GPT API 키 설정 후 hasGptApiKey는 true이다")
    void updateGptApiKey_setsKey() {
        Member member = Member.create("id", "user", "url");
        member.updateGptApiKey("sk-openai-123");

        assertThat(member.hasGptApiKey()).isTrue();
    }

    @Test
    @DisplayName("Gemini API 키 설정 후 hasGeminiApiKey는 true이다")
    void updateGeminiApiKey_setsKey() {
        Member member = Member.create("id", "user", "url");
        member.updateGeminiApiKey("gemini-key-123");

        assertThat(member.hasGeminiApiKey()).isTrue();
    }

    @Test
    @DisplayName("GitHub 크리덴셜 설정 후 hasGithubToken은 true이다")
    void updateGithubCredentials_setsToken() {
        Member member = Member.create("id", "user", "url");
        member.updateGithubCredentials("ghp_token", null, null);

        assertThat(member.hasGithubToken()).isTrue();
    }

    @Test
    @DisplayName("null 값으로 GitHub 크리덴셜 업데이트해도 기존 값이 유지된다")
    void updateGithubCredentials_nullDoesNotOverwrite() {
        Member member = Member.create("id", "user", "url");
        member.updateGithubCredentials("ghp_token", "client-id", "client-secret");
        member.updateGithubCredentials(null, null, null);

        assertThat(member.hasGithubToken()).isTrue();
        assertThat(member.hasGithubClientId()).isTrue();
    }
}
