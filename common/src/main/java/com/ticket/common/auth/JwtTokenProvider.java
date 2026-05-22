package com.ticket.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 工具类:生成 / 解析 / 校验 Token,user 与 admin 模块共享一套签发体系
 *
 * Token claim:
 *   sub      = userId
 *   username = 用户名
 *   roles    = 角色列表(如 ["USER"], ["ADMIN", "USER"])
 *
 * 密钥与过期时间均由配置注入,生产环境必须通过环境变量 JWT_SECRET 注入
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final long expireMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expire-ms:7200000}") long expireMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 未配置或长度不足 256 位,启动失败");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMs;
    }

    public String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())                       // JTI:用于登出黑名单
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles != null ? roles : Collections.emptyList())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object raw = parseClaims(token).get(CLAIM_ROLES);
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        return Collections.emptyList();
    }

    /** 获取 JTI(token 唯一标识),用于黑名单查询 */
    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    /** 获取 token 过期时间戳(毫秒),用于设置黑名单 Redis key 的 TTL */
    public long getExpirationMs(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
