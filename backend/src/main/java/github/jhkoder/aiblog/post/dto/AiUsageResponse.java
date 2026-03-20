package github.jhkoder.aiblog.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiUsageResponse {
    // 전체 AI 호출 횟수 (일일, 앱 내부 카운터)
    private int used;
    private int limit;
    private int remaining;

    // Claude Sonnet - 누적 토큰 (앱 내부)
    private long sonnetInputTokens;
    private long sonnetOutputTokens;

    // Claude - API 실시간 Rate Limit (응답 헤더)
    private long claudeTokenLimit;      // -1 = 미조회
    private long claudeTokenRemaining;
    private long claudeRequestLimit;
    private long claudeRequestRemaining;

    // GPT 이미지 (일별)
    private int imageDailyUsed;
    private int imageDailyLimit;
    private int imageDailyRemaining;
    private int imagePerPostLimit;

    // Grok - 누적 토큰 (앱 내부)
    private long grokInputTokens;
    private long grokOutputTokens;

    // Grok - API 실시간 Rate Limit (응답 헤더)
    private long grokTokenLimit;        // -1 = 미조회
    private long grokTokenRemaining;
    private long grokRequestLimit;
    private long grokRequestRemaining;
}
