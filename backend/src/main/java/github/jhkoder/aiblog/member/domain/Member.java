package github.jhkoder.aiblog.member.domain;

import github.jhkoder.aiblog.config.AesGcmEncryptionConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
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

    @Column(length = 2000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String hashnodeToken;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String hashnodePublicationId;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String claudeApiKey;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String grokApiKey;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String gptApiKey;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String geminiApiKey;

    @Column(length = 1000)
    @Convert(converter = AesGcmEncryptionConverter.class)
    private String githubToken;

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
        this.hashnodeToken = null;
        this.hashnodePublicationId = null;
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
        if (githubToken != null) this.githubToken = githubToken;
    }

    public boolean hasGithubToken() {
        return githubToken != null && !githubToken.isBlank();
    }

    public boolean hasHashnodeConnection() {
        return hashnodeToken != null && !hashnodeToken.isBlank();
    }

    public boolean hasClaudeApiKey() {
        return claudeApiKey != null && !claudeApiKey.isBlank();
    }

    public boolean hasGrokApiKey() {
        return grokApiKey != null && !grokApiKey.isBlank();
    }

    public boolean hasGptApiKey() {
        return gptApiKey != null && !gptApiKey.isBlank();
    }

    public boolean hasGeminiApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
