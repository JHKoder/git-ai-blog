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
 * 전체 통합 key: ai_usage:{memberId}:{date}
 * 모델별       key: ai_usage:{memberId}:{model}:{date}
 * TTL: 자정 이후 자동 만료
 * 우선순위: 모델별 한도 > 전체 한도(aiDailyLimit) > 서버 기본값(ai.daily-limit)
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

    // ── 전체 통합 키 (기존 방식 유지) ──────────────────────────────────────────
    private String key(Long memberId) {
        return KEY_PREFIX + memberId + ":" + LocalDate.now();
    }

    // ── 모델별 키 ──────────────────────────────────────────────────────────────
    private String modelKey(Long memberId, String model) {
        return KEY_PREFIX + memberId + ":" + model + ":" + LocalDate.now();
    }

    /** 전체 통합 유효 한도 (모델 무관) */
    public int getEffectiveLimit(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getAiDailyLimit)
                .filter(limit -> limit != null && limit > 0)
                .orElse(defaultDailyLimit);
    }

    /** 모델별 유효 한도. 모델 한도 설정 없으면 전체 한도로 fallback */
    public int getEffectiveLimit(Long memberId, String model) {
        return memberRepository.findById(memberId)
                .map(member -> modelLimitOf(member, model))
                .filter(limit -> limit != null && limit > 0)
                .orElseGet(() -> getEffectiveLimit(memberId));
    }

    private Integer modelLimitOf(Member member, String model) {
        if (model == null) return null;
        return switch (model) {
            case "claude-sonnet-4-6", "claude-opus-4-5" -> member.getClaudeDailyLimit();
            case "grok-3" -> member.getGrokDailyLimit();
            case "gpt-4o", "gpt-4o-mini" -> member.getGptDailyLimit();
            case "gemini-2.0-flash" -> member.getGeminiDailyLimit();
            default -> null;
        };
    }

    /** 전체 통합 체크 */
    public void check(Long memberId) {
        int limit = getEffectiveLimit(memberId);
        int used = getUsedCount(memberId);
        if (used >= limit) {
            throw new RateLimitException("오늘 AI 호출 한도(" + limit + "회)를 초과했습니다.");
        }
    }

    /** 모델별 체크 */
    public void check(Long memberId, String model) {
        int limit = getEffectiveLimit(memberId, model);
        int used = getUsedCount(memberId, model);
        if (used >= limit) {
            throw new RateLimitException("오늘 " + model + " 호출 한도(" + limit + "회)를 초과했습니다.");
        }
    }

    /** 전체 통합 카운트 증가 */
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

    /** 모델별 카운트 증가 */
    public void increment(Long memberId, String model) {
        String key = modelKey(memberId, model);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, ttlUntilMidnight());
            }
        } catch (Exception e) {
            log.warn("Redis AI 사용량 증가 실패 ({}): {}", model, e.getMessage());
        }
    }

    /** 전체 통합 사용 횟수 */
    public int getUsedCount(Long memberId) {
        try {
            String val = redisTemplate.opsForValue().get(key(memberId));
            return val == null ? 0 : Integer.parseInt(val);
        } catch (Exception e) {
            log.warn("Redis AI 사용량 조회 실패: {}", e.getMessage());
            return 0;
        }
    }

    /** 모델별 사용 횟수 */
    public int getUsedCount(Long memberId, String model) {
        try {
            String val = redisTemplate.opsForValue().get(modelKey(memberId, model));
            return val == null ? 0 : Integer.parseInt(val);
        } catch (Exception e) {
            log.warn("Redis AI 사용량 조회 실패 ({}): {}", model, e.getMessage());
            return 0;
        }
    }

    public int getRemainingCount(Long memberId) {
        return Math.max(0, getEffectiveLimit(memberId) - getUsedCount(memberId));
    }

    public int getRemainingCount(Long memberId, String model) {
        return Math.max(0, getEffectiveLimit(memberId, model) - getUsedCount(memberId, model));
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
