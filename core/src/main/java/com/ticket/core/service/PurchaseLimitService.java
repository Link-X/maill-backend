package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 购买限制服务 — 基于 Redis 实现用户在单个场次的购票数量限制
 */
@Service
public class PurchaseLimitService {

    /** 兜底最小 TTL(秒):场次已结束或时间异常时使用,确保 key 不会被立即清掉 */
    private static final long MIN_TTL_SECONDS = 3600;

    /**
     * 原子性检查并递增购买数量 Lua 脚本：
     * 读取当前购买数量，如果已达到限额则返回 0，否则递增计数并返回 1
     * ARGV[1]=limit, ARGV[2]=seatCount, ARGV[3]=ttlSeconds(仅首次创建 key 时设置)
     */
    private static final DefaultRedisScript<Long> CHECK_AND_INCR_SCRIPT;

    /**
     * 安全递减购买数量 Lua 脚本：
     * 检查当前值，如果不存在或值 <= 0 则返回 0，否则递减计数并返回新值
     */
    private static final DefaultRedisScript<Long> DECR_SAFE_SCRIPT;

    static {
        CHECK_AND_INCR_SCRIPT = new DefaultRedisScript<>();
        CHECK_AND_INCR_SCRIPT.setResultType(Long.class);
        CHECK_AND_INCR_SCRIPT.setScriptText(
            "local count = redis.call('GET', KEYS[1])\n" +
            "if count == false then count = 0 else count = tonumber(count) end\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local seatCount = tonumber(ARGV[2])\n" +
            "local ttl = tonumber(ARGV[3])\n" +
            "if count + seatCount > limit then return 0 end\n" +
            "local newCount = redis.call('INCRBY', KEYS[1], seatCount)\n" +
            "if newCount == seatCount and ttl > 0 then\n" +
            "  redis.call('EXPIRE', KEYS[1], ttl)\n" +
            "end\n" +
            "return 1"
        );

        DECR_SAFE_SCRIPT = new DefaultRedisScript<>();
        DECR_SAFE_SCRIPT.setResultType(Long.class);
        DECR_SAFE_SCRIPT.setScriptText(
            "local v = redis.call('GET', KEYS[1])\n" +
            "if v == false or tonumber(v) <= 0 then return 0 end\n" +
            "local seatCount = tonumber(ARGV[1])\n" +
            "local current = tonumber(v)\n" +
            "if current <= seatCount then\n" +
            "  redis.call('SET', KEYS[1], 0)\n" +
            "  return 0\n" +
            "end\n" +
            "return redis.call('DECRBY', KEYS[1], seatCount)"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public PurchaseLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子性检查并递增购买数量（如果未达到限额）
     *
     * @param sessionId   场次 ID
     * @param userId      用户 ID
     * @param limit       购票限额
     * @param seatCount   本次购买座位数
     * @param ttlSeconds  Redis key 过期时间(秒),应等于"场次结束时间 - 当前时间"
     *                    保证 key 在场次结束前不过期,避免限购被绕过;
     *                    传 <=0 时使用兜底值 {@value #MIN_TTL_SECONDS} 秒
     * @return 如果成功递增（未达到限额）返回 true，否则返回 false
     */
    public boolean checkAndIncrement(long sessionId, long userId, int limit, int seatCount, long ttlSeconds) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : MIN_TTL_SECONDS;
        Long result = redisTemplate.execute(
                CHECK_AND_INCR_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(seatCount),
                String.valueOf(effectiveTtl)
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 递减购买数量（可能的退票场景）
     * 使用 Lua 脚本保证计数不低于 0
     *
     * @param sessionId 场次 ID
     * @param userId    用户 ID
     */
    public void decrement(long sessionId, long userId, int seatCount) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        redisTemplate.execute(DECR_SAFE_SCRIPT, Collections.singletonList(key), String.valueOf(seatCount));
    }

    /**
     * 将计数重置为指定值（用于服务重启后的自愈修正）
     */
    public void resetCount(long sessionId, long userId, int count) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        if (count <= 0) {
            redisTemplate.delete(key);
        } else {
            redisTemplate.opsForValue().set(key, String.valueOf(count));
        }
    }

}
