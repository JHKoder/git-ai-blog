package github.jhkoder.aiblog.member.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ApiKeyUpdateRequest {
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

    @Size(max = 200, message = "GitHub Client ID는 200자 이하여야 합니다.")
    private String githubClientId;

    @Size(max = 500, message = "GitHub Client Secret은 500자 이하여야 합니다.")
    private String githubClientSecret;
}
