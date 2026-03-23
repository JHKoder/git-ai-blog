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


}
