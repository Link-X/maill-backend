package com.ticket.common.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 吊销黑名单:登出时把 JTI 写入 Redis,过滤器校验时拒绝.
 *
 * key 形式: jwt:blacklist:{jti}, TTL 自动覆盖 token 剩余有效期,避免长期占用 Redis.
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 把 JTI 加入黑名单
     *
     * @param jti       token id
     * @param ttlMillis 剩余有效期(毫秒),最小 1 秒
     */
    public void revoke(String jti, long ttlMillis) {
        if (jti == null || jti.isEmpty()) return;
        long ttl = Math.max(1000, ttlMillis);
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl, TimeUnit.MILLISECONDS);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
