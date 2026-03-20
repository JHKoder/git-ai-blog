package github.jhkoder.aiblog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh Token 서비스 (Redis 기반).
 * - 토큰 발급: UUID 생성 후 Redis에 "refresh_token:{token}" → memberId 저장
 * - 검증: Redis에 존재 여부 + blacklist 미포함 여부 확인
 * - Rotation: 기존 토큰 무효화 후 새 토큰 발급
 * - Blacklist: 로그아웃 시 토큰을 "refresh_blacklist:{token}" 키로 등록 (잔여 TTL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "refresh_blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;

    /**
     * Refresh Token을 신규 발급하고 Redis에 저장한다.
     *
     * @param memberId 회원 ID
     * @param ttl      토큰 유효 기간
     * @return 발급된 Refresh Token 값
     */
    public String issue(Long memberId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, String.valueOf(memberId), ttl);
        return token;
    }

    /**
     * Refresh Token 유효성을 검증한다.
     * blacklist에 있으면 무효, Redis에 없으면 만료된 것으로 처리한다.
     *
     * @param token Refresh Token 값
     * @return 유효하면 true
     */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        if (isBlacklisted(token)) return false;
        return redisTemplate.hasKey(TOKEN_PREFIX + token) == Boolean.TRUE;
    }

    /**
     * Refresh Token에서 memberId를 조회한다.
     *
     * @param token Refresh Token 값
     * @return memberId, 없으면 null
     */
    public Long getMemberId(String token) {
        String val = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (val == null) return null;
        return Long.parseLong(val);
    }

    /**
     * 기존 Refresh Token을 무효화하고 새 토큰을 발급한다 (Rotation).
     *
     * @param oldToken 이전 Refresh Token
     * @param memberId 회원 ID
     * @param ttl      새 토큰 유효 기간
     * @return 새로 발급된 Refresh Token
     */
    public String rotate(String oldToken, Long memberId, Duration ttl) {
        invalidate(oldToken, ttl);
        return issue(memberId, ttl);
    }

    /**
     * Refresh Token을 blacklist에 등록하고 Redis 저장값을 삭제한다.
     *
     * @param token 무효화할 Refresh Token
     * @param ttl   blacklist 유지 기간 (잔여 유효시간)
     */
    public void invalidate(String token, Duration ttl) {
        if (token == null || token.isBlank()) return;
        redisTemplate.delete(TOKEN_PREFIX + token);
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + token, "1", ttl);
    }

    private boolean isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token) == Boolean.TRUE;
    }
}
