package github.jhkoder.aiblog.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * AI 모델별 토큰 사용량 월별 누적 트래킹 (Redis 기반).
 * key: token_usage:{memberId}:{model}:{yyyyMM}:input|output
 * TTL: 월말 + 1일 자동 만료 (~32일)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenUsageTracker {

    private static final String KEY_PREFIX = "token_usage:";
    private static final Duration MONTHLY_TTL = Duration.ofDays(32);

    private final StringRedisTemplate redisTemplate;

    public record ModelUsage(long inputTokens, long outputTokens) {
        public long total() { return inputTokens + outputTokens; }
    }

    private String inputKey(Long memberId, String model) {
        String month = LocalDate.now().getYear() + String.format("%02d", LocalDate.now().getMonthValue());
        return KEY_PREFIX + memberId + ":" + model + ":" + month + ":input";
    }

    private String outputKey(Long memberId, String model) {
        String month = LocalDate.now().getYear() + String.format("%02d", LocalDate.now().getMonthValue());
        return KEY_PREFIX + memberId + ":" + model + ":" + month + ":output";
    }

    public void record(Long memberId, String model, long inputTokens, long outputTokens) {
        try {
            String iKey = inputKey(memberId, model);
            String oKey = outputKey(memberId, model);
            Long iCount = redisTemplate.opsForValue().increment(iKey, inputTokens);
            Long oCount = redisTemplate.opsForValue().increment(oKey, outputTokens);
            if (iCount != null && iCount == inputTokens) {
                redisTemplate.expire(iKey, MONTHLY_TTL);
            }
            if (oCount != null && oCount == outputTokens) {
                redisTemplate.expire(oKey, MONTHLY_TTL);
            }
        } catch (Exception e) {
            log.warn("Redis 토큰 사용량 기록 실패: {}", e.getMessage());
        }
    }

    public ModelUsage getUsage(Long memberId, String model) {
        try {
            String iVal = redisTemplate.opsForValue().get(inputKey(memberId, model));
            String oVal = redisTemplate.opsForValue().get(outputKey(memberId, model));
            return new ModelUsage(
                iVal == null ? 0 : Long.parseLong(iVal),
                oVal == null ? 0 : Long.parseLong(oVal)
            );
        } catch (Exception e) {
            log.warn("Redis 토큰 사용량 조회 실패: {}", e.getMessage());
            return new ModelUsage(0, 0);
        }
    }
}
