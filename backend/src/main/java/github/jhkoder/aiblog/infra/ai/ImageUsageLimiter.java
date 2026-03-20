package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.common.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * GPT 이미지 생성 사용량 제한기 (Redis 기반).
 * - 하루 최대 10장 (key: img_usage:{memberId}:{date}:daily)
 * - 게시글당 최대 5장 (controller 계층에서 별도 카운트)
 * TTL: Redis 자동 만료 — @Scheduled 불필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageUsageLimiter {

    public static final int MAX_PER_DAY = 10;
    public static final int MAX_PER_POST = 5;

    private static final String DAILY_PREFIX = "img_usage:";

    private final StringRedisTemplate redisTemplate;

    private String dailyKey(Long memberId) {
        return DAILY_PREFIX + memberId + ":" + LocalDate.now() + ":daily";
    }

    public void checkDaily(Long memberId) {
        int used = getDailyUsed(memberId);
        if (used >= MAX_PER_DAY) {
            throw new RateLimitException("오늘 이미지 생성 한도(" + MAX_PER_DAY + "장)를 초과했습니다.");
        }
    }

    public void increment(Long memberId) {
        String key = dailyKey(memberId);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, ttlUntilMidnight());
            }
        } catch (Exception e) {
            log.warn("Redis 이미지 사용량 증가 실패: {}", e.getMessage());
        }
    }

    public int getDailyUsed(Long memberId) {
        try {
            String val = redisTemplate.opsForValue().get(dailyKey(memberId));
            return val == null ? 0 : Integer.parseInt(val);
        } catch (Exception e) {
            log.warn("Redis 이미지 사용량 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    public int getDailyRemaining(Long memberId) {
        return Math.max(0, MAX_PER_DAY - getDailyUsed(memberId));
    }

    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight);
    }
}
