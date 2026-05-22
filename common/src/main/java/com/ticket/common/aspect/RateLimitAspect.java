package com.ticket.common.aspect;

import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.common.annotation.RateLimits;
import com.ticket.common.constant.RedisKeys;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.service.BlacklistService;
import com.ticket.common.util.RateLimitService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Aspect
@Component
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final BlacklistService blacklistService;
    /** 可信代理 IP 白名单：只有来自这些 IP 的请求才信任 X-Forwarded-For 头 */
    private final Set<String> trustedProxies;

    public RateLimitAspect(RateLimitService rateLimitService,
                           BlacklistService blacklistService,
                           @Value("${rate-limit.trusted-proxies:}") String trustedProxiesConfig) {
        this.rateLimitService = rateLimitService;
        this.blacklistService = blacklistService;
        if (trustedProxiesConfig == null || trustedProxiesConfig.isBlank()) {
            this.trustedProxies = Collections.emptySet();
        } else {
            Set<String> set = new HashSet<>();
            for (String s : trustedProxiesConfig.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
            this.trustedProxies = Collections.unmodifiableSet(set);
        }
    }

    @Before("@annotation(com.ticket.common.annotation.RateLimit) || " +
            "@annotation(com.ticket.common.annotation.RateLimits)")
    public void check(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        List<RateLimit> limits = collectAnnotations(method);

        String ip = getClientIp();
        Long userId = getCurrentUserId();
        String methodKey = method.getDeclaringClass().getSimpleName() + "." + method.getName();

        // 黑名单优先检查，不命中再做计数限流
        for (RateLimit limit : limits) {
            if (limit.type() == LimitType.BLACKLIST) {
                checkBlacklist(userId, ip);
            }
        }

        for (RateLimit limit : limits) {
            if (limit.type() == LimitType.BLACKLIST) continue;
            enforce(limit, methodKey, userId, ip);
        }
    }

    private void checkBlacklist(Long userId, String ip) {
        if (userId != null && blacklistService.isUserBlacklisted(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被限制");
        }
        if (ip != null && blacklistService.isIpBlacklisted(ip)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "IP 已被限制");
        }
    }

    private void enforce(RateLimit limit, String methodKey, Long userId, String ip) {
        long windowSecond = System.currentTimeMillis() / 1000 / limit.window();
        String key = buildKey(limit.type(), methodKey, userId, ip, windowSecond);

        boolean allowed = rateLimitService.isAllowed(key, limit.limit(), limit.window());
        if (!allowed) {
            String msg = limit.message().isEmpty()
                    ? ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()
                    : limit.message();
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, msg);
        }
    }

    private String buildKey(LimitType type, String methodKey, Long userId, String ip, long windowSecond) {
        switch (type) {
            case GLOBAL:
                return RedisKeys.rateLimitGlobal(methodKey, windowSecond);
            case USER:
                if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
                return RedisKeys.rateLimitUser(userId, methodKey, windowSecond);
            case IP:
                return RedisKeys.rateLimitIp(ip != null ? ip : "unknown", methodKey, windowSecond);
            default:
                throw new IllegalArgumentException("未知限流类型: " + type);
        }
    }

    private List<RateLimit> collectAnnotations(Method method) {
        List<RateLimit> result = new ArrayList<>();
        RateLimits container = method.getAnnotation(RateLimits.class);
        if (container != null) {
            result.addAll(Arrays.asList(container.value()));
        }
        RateLimit single = method.getAnnotation(RateLimit.class);
        if (single != null) {
            result.add(single);
        }
        return result;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 获取客户端真实 IP。
     *
     * 仅当请求的直接来源 (RemoteAddr) 在可信代理白名单中时,才信任 X-Forwarded-For / X-Real-IP 头;
     * 否则直接使用 RemoteAddr,防止客户端伪造 IP 绕过限流。
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String remoteAddr = request.getRemoteAddr();
            if (!trustedProxies.contains(remoteAddr)) {
                return remoteAddr;
            }
            // 直接来源是可信代理,可以读 X-Forwarded-For 中最左侧的 IP 作为真实客户端
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                return remoteAddr;
            }
            return ip.contains(",") ? ip.split(",")[0].trim() : ip;
        } catch (Exception e) {
            return null;
        }
    }
}
