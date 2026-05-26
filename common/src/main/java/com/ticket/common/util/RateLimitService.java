package com.ticket.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RateLimitService {

    /**
     * 滑动窗口限流 Lua 脚本（基于 ZSET）：
     *  - KEYS[1]: 限流 key
     *  - ARGV[1]: 当前时间(ms)
     *  - ARGV[2]: 窗口大小(ms)
     *  - ARGV[3]: 窗口内最大请求数
     *  - ARGV[4]: 本次插入 ZSET 的随机后缀(避免 member 冲突)
     *  返回: 1=放行, 0=限流
     *
     * 相比"固定窗口 INCR + EXPIRE",滑动窗口在秒级边界没有 2x 流量突刺问题:
     * 旧实现下 [59s, 61s] 区间可能放行 2x limit 的请求。
     */
    private static final DefaultRedisScript<Long> SLIDING_SCRIPT;

    static {
        SLIDING_SCRIPT = new DefaultRedisScript<>();
        SLIDING_SCRIPT.setResultType(Long.class);
        SLIDING_SCRIPT.setScriptText(
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local window = tonumber(ARGV[2])\n" +
            "local limit = tonumber(ARGV[3])\n" +
            "local suffix = ARGV[4]\n" +
            // 清理窗口外的旧请求
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
            "local count = redis.call('ZCARD', key)\n" +
            "if count >= limit then\n" +
            "    return 0\n" +
            "end\n" +
            // member = "时间戳:随机后缀" 保证 ZADD 不会因 member 重复而失败
            "redis.call('ZADD', key, now, now .. ':' .. suffix)\n" +
            // 窗口 + 1s 兜底过期,防止冷 key 残留
            "redis.call('PEXPIRE', key, window + 1000)\n" +
            "return 1"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 滑动窗口限流(平滑放行,无边界突刺)。
     *
     * @param key           Redis key(由调用方拼好,无需含时间窗后缀)
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口大小(秒)
     * @return true=放行, false=触发限流
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String suffix = String.valueOf(ThreadLocalRandom.current().nextLong());
        Long result = redisTemplate.execute(
                SLIDING_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(limit),
                suffix
        );
        return Long.valueOf(1L).equals(result);
    }
}
