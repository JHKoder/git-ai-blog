package github.jhkoder.aiblog.infra.ai;

import github.jhkoder.aiblog.common.exception.RateLimitException;
import github.jhkoder.aiblog.member.domain.Member;
import github.jhkoder.aiblog.member.domain.MemberRepository;
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
 * 우선순위: 사용자 설정 aiDailyLimit > 서버 기본값(ai.daily-limit)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageLimiter {

    private static final String KEY_PREFIX = "ai_usage:";

    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    @Getter
    @Value("${ai.daily-limit:5}")
    private int defaultDailyLimit;

    private String key(Long memberId) {
        return KEY_PREFIX + memberId + ":" + LocalDate.now();
    }

    public int getEffectiveLimit(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getAiDailyLimit)
                .filter(limit -> limit != null && limit > 0)
                .orElse(defaultDailyLimit);
    }

    public void check(Long memberId) {
        int limit = getEffectiveLimit(memberId);
        int used = getUsedCount(memberId);
        if (used >= limit) {
            throw new RateLimitException("오늘 AI 호출 한도(" + limit + "회)를 초과했습니다.");
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
        return Math.max(0, getEffectiveLimit(memberId) - getUsedCount(memberId));
    }

    /** @deprecated Use getEffectiveLimit(memberId) for member-aware limit */
    @Deprecated
    public int getDailyLimit() {
        return defaultDailyLimit;
    }

    private Duration ttlUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight);
    }
}
