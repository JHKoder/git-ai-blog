package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.common.exception.RateLimitException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 호출 횟수 일별 제한기 (Redis 기반).
 * key: ai_usage:{memberId}:{date} (yyyy-MM-dd)
 * TTL: 자정 이후 자동 만료 — @Scheduled 불필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageLimiter {

    private static final String KEY_PREFIX = "ai_usage:";

    private final StringRedisTemplate redisTemplate;

    @Getter
    @Value("${ai.daily-limit:5}")
    private int dailyLimit;

    private String key(Long memberId) {
        return KEY_PREFIX + memberId + ":" + LocalDate.now();
    }

    public void check(Long memberId) {
        int used = getUsedCount(memberId);
        if (used >= dailyLimit) {
            throw new RateLimitException("오늘 AI 호출 한도(" + dailyLimit + "회)를 초과했습니다.");
        }
    }

    public void increment(Long memberId) {
        String key = key(memberId);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, ttlUntilMidnight());
            }
        } catch (Exception e) {
            log.warn("Redis AI 사용량 증가 실패: {}", e.getMessage());
        }
    }

    public int getUsedCount(Long memberId) {
        try {
            String val = redisTemplate.opsForValue().get(key(memberId));
            return val == null ? 0 : Integer.parseInt(val);
        } catch (Exception e) {
            log.warn("Redis AI 사용량 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    public int getRemainingCount(Long memberId) {
        return Math.max(0, dailyLimit - getUsedCount(memberId));
    }

    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight);
    }
}
