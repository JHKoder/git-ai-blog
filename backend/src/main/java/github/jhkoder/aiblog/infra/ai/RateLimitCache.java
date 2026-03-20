package github.jhkoder.aiblog.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI API 응답 헤더에서 파싱한 실시간 Rate Limit 정보 캐시 (Redis 기반).
 * key: rl_cache:{memberId}:{provider}
 * 값: "tokenLimit,tokenRemaining,requestLimit,requestRemaining" (CSV)
 * TTL: 1시간 (rate limit 정보는 짧은 주기로 갱신됨)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCache {

    private static final String KEY_PREFIX = "rl_cache:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public record RateLimitInfo(long tokenLimit, long tokenRemaining, long requestLimit, long requestRemaining) {
        public static RateLimitInfo unknown() {
            return new RateLimitInfo(-1, -1, -1, -1);
        }
        public boolean isKnown() { return tokenLimit >= 0; }
    }

    private String key(Long memberId, String provider) {
        return KEY_PREFIX + memberId + ":" + provider;
    }

    public void update(Long memberId, String provider, RateLimitInfo info) {
        try {
            String value = info.tokenLimit() + "," + info.tokenRemaining() + "," +
                           info.requestLimit() + "," + info.requestRemaining();
            redisTemplate.opsForValue().set(key(memberId, provider), value, TTL);
        } catch (Exception e) {
            log.warn("Redis Rate Limit 캐시 저장 실패: {}", e.getMessage());
        }
    }

    public RateLimitInfo get(Long memberId, String provider) {
        try {
            String val = redisTemplate.opsForValue().get(key(memberId, provider));
            if (val == null) return RateLimitInfo.unknown();
            String[] parts = val.split(",");
            if (parts.length != 4) return RateLimitInfo.unknown();
            return new RateLimitInfo(
                Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                Long.parseLong(parts[2]), Long.parseLong(parts[3])
            );
        } catch (Exception e) {
            log.warn("Redis Rate Limit 캐시 조회 실패: {}", e.getMessage());
            return RateLimitInfo.unknown();
        }
    }
}
