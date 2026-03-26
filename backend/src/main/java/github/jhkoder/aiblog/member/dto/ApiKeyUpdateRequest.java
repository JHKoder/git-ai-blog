package github.jhkoder.aiblog.member.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ApiKeyUpdateRequest {

    @Min(value = 1, message = "AI 일일 한도는 1 이상이어야 합니다.")
    @Max(value = 1000, message = "AI 일일 한도는 1000 이하여야 합니다.")
    private Integer aiDailyLimit;

    @Min(value = 1, message = "Claude 일일 한도는 1 이상이어야 합니다.")
    @Max(value = 1000, message = "Claude 일일 한도는 1000 이하여야 합니다.")
    private Integer claudeDailyLimit;

    @Min(value = 1, message = "Grok 일일 한도는 1 이상이어야 합니다.")
    @Max(value = 1000, message = "Grok 일일 한도는 1000 이하여야 합니다.")
    private Integer grokDailyLimit;

    @Min(value = 1, message = "GPT 일일 한도는 1 이상이어야 합니다.")
    @Max(value = 1000, message = "GPT 일일 한도는 1000 이하여야 합니다.")
    private Integer gptDailyLimit;

    @Min(value = 1, message = "Gemini 일일 한도는 1 이상이어야 합니다.")
    @Max(value = 1000, message = "Gemini 일일 한도는 1000 이하여야 합니다.")
    private Integer geminiDailyLimit;

    @Size(max = 500, message = "Claude API 키는 500자 이하여야 합니다.")
    private String claudeApiKey;

    @Size(max = 500, message = "Grok API 키는 500자 이하여야 합니다.")
    private String grokApiKey;

    @Size(max = 500, message = "GPT API 키는 500자 이하여야 합니다.")
    private String gptApiKey;

    @Size(max = 500, message = "Gemini API 키는 500자 이하여야 합니다.")
    private String geminiApiKey;

    @Size(max = 500, message = "GitHub Token은 500자 이하여야 합니다.")
    private String githubToken;

    private Boolean clearClaudeApiKey;
    private Boolean clearGrokApiKey;
    private Boolean clearGptApiKey;
    private Boolean clearGeminiApiKey;
    private Boolean clearGithubToken;

    /**
     * Hashnode 태그 매핑 테이블 — JSON 배열 문자열.
     * 형식: [{"name":"java","slug":"java","id":"..."},...]
     */
    @Size(max = 5000, message = "Hashnode 태그 설정은 5000자 이하여야 합니다.")
    private String hashnodeTags;
}
