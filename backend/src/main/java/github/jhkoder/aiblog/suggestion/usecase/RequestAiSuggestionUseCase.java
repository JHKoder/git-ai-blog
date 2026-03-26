package github.jhkoder.aiblog.suggestion.usecase;

import github.jhkoder.aiblog.infra.ai.AiUsageLimiter;
import github.jhkoder.aiblog.suggestion.dto.AiSuggestionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 개선 요청 진입점.
 * 1. 사용량 한도 즉시 체크 (초과 시 429)
 * 2. 비동기 처리 위임 → 컨트롤러는 202 Accepted 반환
 * 3. 실제 AI 처리는 AsyncAiSuggestionService.executeAsync()에서 수행
 */
@Service
@RequiredArgsConstructor
public class RequestAiSuggestionUseCase {

    private final AiUsageLimiter aiUsageLimiter;
    private final AsyncAiSuggestionService asyncAiSuggestionService;

    public void execute(Long postId, Long memberId, AiSuggestionRequest request) {
        aiUsageLimiter.check(memberId);
        asyncAiSuggestionService.executeAsync(postId, memberId, request);
    }
}
