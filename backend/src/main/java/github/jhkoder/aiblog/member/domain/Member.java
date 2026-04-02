package github.jhkoder.aiblog.member.domain;

import github.jhkoder.aiblog.config.AesGcmEncryptionConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "members", indexes = {
        @Index(name = "idx_members_github_id", columnList = "githubId", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String githubId;

    @Column(nullable = false)
    private String username;

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String hashnodeToken;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String hashnodePublicationId;

    /**
     * Hashnode 태그 매핑 테이블 — JSON 배열 문자열로 저장.
     * 형식: [{"name":"java","slug":"java","id":"..."},...]
     * 발행 시 로컬 태그명 → Hashnode tag ID 매핑에 사용.
     */
    @Column(columnDefinition = "TEXT")
    private String hashnodeTags;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String claudeApiKey;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String grokApiKey;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String gptApiKey;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String geminiApiKey;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String githubToken;

    private Integer aiDailyLimit;

    private Integer claudeDailyLimit;
    private Integer grokDailyLimit;
    private Integer gptDailyLimit;
    private Integer geminiDailyLimit;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Member create(String githubId, String username, String avatarUrl) {
        Member member = new Member();
        member.githubId = githubId;
        member.username = username;
        member.avatarUrl = avatarUrl;
        return member;
    }

    public void updateProfile(String username, String avatarUrl) {
        this.username = username;
        this.avatarUrl = avatarUrl;
    }

    public void connectHashnode(String token, String publicationId) {
        this.hashnodeToken = token;
        this.hashnodePublicationId = publicationId;
    }

    public void disconnectHashnode() {
        this.hashnodeToken = "";
        this.hashnodePublicationId = "";
    }

    public void updateClaudeApiKey(String key) {
        this.claudeApiKey = key;
    }

    public void updateGrokApiKey(String key) {
        this.grokApiKey = key;
    }

    public void updateGptApiKey(String key) {
        this.gptApiKey = key;
    }

    public void updateGeminiApiKey(String key) {
        this.geminiApiKey = key;
    }

    public void updateGithubCredentials(String githubToken) {
        this.githubToken = Optional.ofNullable(githubToken).orElse(this.githubToken);
    }

    public boolean hasGithubToken() {
        return isPresent(githubToken);
    }

    public boolean hasHashnodeConnection() {
        return isPresent(hashnodeToken);
    }

    public boolean hasClaudeApiKey() {
        return isPresent(claudeApiKey);
    }

    public boolean hasGrokApiKey() {
        return isPresent(grokApiKey);
    }

    public boolean hasGptApiKey() {
        return isPresent(gptApiKey);
    }

    public boolean hasGeminiApiKey() {
        return isPresent(geminiApiKey);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    public void updateHashnodeTags(String hashnodeTagsJson) {
        this.hashnodeTags = hashnodeTagsJson;
    }

    public void updateAiDailyLimit(Integer limit) {
        this.aiDailyLimit = limit;
    }

    public void updateClaudeDailyLimit(Integer limit) {
        this.claudeDailyLimit = limit;
    }

    public void updateGrokDailyLimit(Integer limit) {
        this.grokDailyLimit = limit;
    }

    public void updateGptDailyLimit(Integer limit) {
        this.gptDailyLimit = limit;
    }

    public void updateGeminiDailyLimit(Integer limit) {
        this.geminiDailyLimit = limit;
    }
}
