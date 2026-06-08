package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 座位库存服务 — 基于 Redis 实现座位池管理、锁定与释放
 *
 * 支持两种售卖模式(sale_mode):
 *  1. 选座模式 — 用户挑具体座位,沿用 session:seats + seat:lock 的 SETNX 锁。
 *  2. 派座模式 — 用户选区域+张数,系统从「单座池/情侣对池」派。
 *     情侣座在 warmup 时按 type 分流到独立的 couple 池,单座池里永远不会出现情侣座,
 *     从数据结构层面杜绝"派单座时分到情侣座中的一个"。
 */
@Service
public class SeatInventoryService {

    /** 座位库存默认过期时间：7 天（秒） */
    private static final long INVENTORY_TTL_SECONDS = 7 * 24 * 3600L;

    /**
     * 批量原子锁座 Lua 脚本(选座模式):
     * 遍历所有 seatId,依次执行 SETNX;任意一个已被锁定则回滚已锁定的 key 后返回 0,全部成功返回 1。
     */
    private static final DefaultRedisScript<Long> BATCH_LOCK_SCRIPT;

    /**
     * 原子释放座位 Lua 脚本(选座模式):
     * 删除锁 key 并将 seatId 重新加入可售集合,两步操作原子化。
     */
    private static final DefaultRedisScript<Long> RELEASE_SEAT_SCRIPT;

    /**
     * 原子消费座位 Lua脚本:
     * 验证锁的持有者后,原子执行:从可售集合移除 + 删除锁
     */
    private static final DefaultRedisScript<Long> CONSUME_SEAT_SCRIPT;

    /**
     * 派座模式:仅扣库存(不取池)Lua 脚本
     *  KEYS[1] = 库存计数器
     *  ARGV[1] = quantity
     *  返回:1=成功,0=库存不足
     */
    private static final DefaultRedisScript<Long> RESERVE_STOCK_SCRIPT;

    /**
     * 派座模式:仅取池中 N 个 member(不动库存)Lua 脚本
     *  KEYS[1] = 座位池 ZSET
     *  ARGV[1] = quantity
     *  返回:座位 member 列表(单座是 seatId,情侣是 "left:right");池子不足返回空 list 并把已弹出的塞回去
     */
    private static final DefaultRedisScript<List> POP_POOL_SCRIPT;

    /**
     * 派座模式:回滚库存 + 还座位到池 Lua 脚本
     *  KEYS[1] = 库存计数器, KEYS[2] = 座位池 ZSET
     *  ARGV: [quantity, member1, score1, member2, score2, ...]
     */
    private static final DefaultRedisScript<Long> RELEASE_TO_POOL_SCRIPT;

    static {
        BATCH_LOCK_SCRIPT = new DefaultRedisScript<>();
        BATCH_LOCK_SCRIPT.setResultType(Long.class);
        BATCH_LOCK_SCRIPT.setScriptText(
            "local sessionId = ARGV[1]\n" +
            "local userId = ARGV[2]\n" +
            "local ttl = ARGV[3]\n" +
            "local sessionKey = 'session:seats:' .. sessionId\n" +
            "local lockedCounterKey = 'session:locked:' .. sessionId\n" +
            "local lockedKeys = {}\n" +
            "for i = 4, #ARGV do\n" +
            "  local seatId = ARGV[i]\n" +
            "  if redis.call('SISMEMBER', sessionKey, seatId) == 0 then\n" +
            "    for _, k in ipairs(lockedKeys) do\n" +
            "      redis.call('DEL', k)\n" +
            "    end\n" +
            "    return 0\n" +
            "  end\n" +
            "  local lockKey = 'seat:lock:' .. sessionId .. ':' .. seatId\n" +
            "  local ok = redis.call('SETNX', lockKey, userId)\n" +
            "  if ok == 0 then\n" +
            "    for _, k in ipairs(lockedKeys) do\n" +
            "      redis.call('DEL', k)\n" +
            "    end\n" +
            "    return 0\n" +
            "  end\n" +
            "  redis.call('EXPIRE', lockKey, ttl)\n" +
            "  table.insert(lockedKeys, lockKey)\n" +
            "end\n" +
            "redis.call('INCRBY', lockedCounterKey, #ARGV - 3)\n" +
            "return 1"
        );

        RELEASE_SEAT_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SEAT_SCRIPT.setResultType(Long.class);
        RELEASE_SEAT_SCRIPT.setScriptText(
            "redis.call('DEL', KEYS[1])\n" +
            "redis.call('SADD', KEYS[2], ARGV[1])\n" +
            "local cur = redis.call('GET', KEYS[3])\n" +
            "if cur and tonumber(cur) > 0 then\n" +
            "  redis.call('DECR', KEYS[3])\n" +
            "end\n" +
            "return 1"
        );

        CONSUME_SEAT_SCRIPT = new DefaultRedisScript<>();
        CONSUME_SEAT_SCRIPT.setResultType(Long.class);
        CONSUME_SEAT_SCRIPT.setScriptText(
                "local sessionKey = KEYS[1]\n" +
                        "local lockKey = KEYS[2]\n" +
                        "local lockedCounterKey = KEYS[3]\n" +
                        "local seatId = ARGV[1]\n" +
                        "local userId = ARGV[2]\n" +
                        "local lockOwner = redis.call('GET', lockKey)\n" +
                        "if not lockOwner then return 0 end\n" +
                        "if lockOwner ~= userId then return 0 end\n" +
                        "redis.call('SREM', sessionKey, seatId)\n" +
                        "redis.call('DEL', lockKey)\n" +
                        "local cur = redis.call('GET', lockedCounterKey)\n" +
                        "if cur and tonumber(cur) > 0 then\n" +
                        "  redis.call('DECR', lockedCounterKey)\n" +
                        "end\n" +
                        "return 1\n"
        );

        // 派座模式:仅扣库存 — DECR 前先 GET 检查,够则 DECRBY,不够返回 0
        RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        RESERVE_STOCK_SCRIPT.setResultType(Long.class);
        RESERVE_STOCK_SCRIPT.setScriptText(
                "local stockKey = KEYS[1]\n" +
                "local qty      = tonumber(ARGV[1])\n" +
                "local cur = tonumber(redis.call('GET', stockKey) or '0')\n" +
                "if cur < qty then return 0 end\n" +
                "redis.call('DECRBY', stockKey, qty)\n" +
                "return 1\n"
        );

        // 派座模式:仅 ZPOPMIN N 个,不动库存。
        // 池子不足时把已弹的塞回去并返回空列表(由上层据此回滚库存)。
        POP_POOL_SCRIPT = new DefaultRedisScript<>();
        POP_POOL_SCRIPT.setResultType(List.class);
        POP_POOL_SCRIPT.setScriptText(
                "local poolKey = KEYS[1]\n" +
                "local qty     = tonumber(ARGV[1])\n" +
                "local popped = redis.call('ZPOPMIN', poolKey, qty)\n" +
                "if (#popped / 2) < qty then\n" +
                "  for i = 1, #popped, 2 do\n" +
                "    redis.call('ZADD', poolKey, tonumber(popped[i+1]), popped[i])\n" +
                "  end\n" +
                "  return {}\n" +
                "end\n" +
                "-- 返回 [member1, score1, member2, score2, ...] 保留 score 给上层记录\n" +
                "return popped\n"
        );

        // 派座模式:回滚 — 库存 INCRBY + 座位 ZADD 回池
        RELEASE_TO_POOL_SCRIPT = new DefaultRedisScript<>();
        RELEASE_TO_POOL_SCRIPT.setResultType(Long.class);
        RELEASE_TO_POOL_SCRIPT.setScriptText(
                "local stockKey = KEYS[1]\n" +
                "local poolKey  = KEYS[2]\n" +
                "local qty      = tonumber(ARGV[1])\n" +
                "redis.call('INCRBY', stockKey, qty)\n" +
                "for i = 2, #ARGV, 2 do\n" +
                "  redis.call('ZADD', poolKey, tonumber(ARGV[i+1]), ARGV[i])\n" +
                "end\n" +
                "return 1\n"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public SeatInventoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 预热座位库存。
     *
     * <p>不论何种售卖模式,所有座位都会写入全场 session:seats 集合(作为"已售/未售"的真相源)。
     * 对于 sale_mode=2 的区域,额外构建两个池子:
     * <ul>
     *   <li>area:pool:single — 仅 type=1 单座</li>
     *   <li>area:pool:couple — 由 type=2/3 成对组成,member="left:right"</li>
     * </ul>
     * 同时初始化 area:stock:single / area:stock:couple 计数器。
     *
     * @param sessionId 场次 ID
     * @param seats     需要写入的座位列表
     * @param areas     需要缓存的价格区域列表(必须包含 sale_mode 字段)
     */
    public void warmup(long sessionId, List<Seat> seats, List<SeatArea> areas) {
        // 1. 区域索引:用于按 areaId 查 sale_mode
        Map<String, SeatArea> areaMap = new HashMap<>();
        for (SeatArea a : areas) {
            areaMap.put(a.getAreaId(), a);
        }

        // 2. 按 (areaId, type) 分组,组装派座池
        //    couple:把 type=2 与对应 type=3 配对成 "left:right" member
        Map<String, List<Seat>> singleByArea = new HashMap<>();
        Map<String, List<String>> coupleMembersByArea = new HashMap<>();
        // 配对辅助:同区域内 seat.id -> seat,便于按 pair_seat_id 找对方
        Map<Long, Seat> seatIndex = new HashMap<>();
        for (Seat s : seats) seatIndex.put(s.getId(), s);

        Set<Long> coupleConsumed = new HashSet<>();

        for (Seat s : seats) {
            String areaId = s.getAreaId() == null ? "" : s.getAreaId();
            SeatArea area = areaMap.get(areaId);
            boolean allocateMode = area != null && area.getSaleMode() != null && area.getSaleMode() == 2;

            if (s.getType() != null && s.getType() == 1) {
                if (allocateMode) {
                    singleByArea.computeIfAbsent(areaId, k -> new ArrayList<>()).add(s);
                }
            } else if (s.getType() != null && (s.getType() == 2 || s.getType() == 3)) {
                // 情侣座 — 用 coupleConsumed 去重,只处理一次配对
                if (coupleConsumed.contains(s.getId())) continue;
                Long pairId = s.getPairSeatId();
                if (pairId == null) continue;
                Seat pair = seatIndex.get(pairId);
                if (pair == null) continue;
                coupleConsumed.add(s.getId());
                coupleConsumed.add(pair.getId());

                if (allocateMode) {
                    // member 用 "leftId:rightId" 形式,left 取 colNo 小的那个
                    Seat left  = s.getColNo() <= pair.getColNo() ? s : pair;
                    Seat right = (left == s) ? pair : s;
                    String member = left.getId() + ":" + right.getId();
                    coupleMembersByArea.computeIfAbsent(areaId, k -> new ArrayList<>()).add(member);
                }
            }
        }

        String sessionKey = RedisKeys.sessionSeats(sessionId);

        // 3. Pipeline 写入 Redis
        redisTemplate.executePipelined((RedisConnection connection) -> {
            byte[] sessionKeyBytes = sessionKey.getBytes(StandardCharsets.UTF_8);

            // 全场 SET — 选座+派座共用的"未售真相源"
            for (Seat seat : seats) {
                connection.sAdd(sessionKeyBytes,
                        String.valueOf(seat.getId()).getBytes(StandardCharsets.UTF_8));
            }
            connection.expire(sessionKeyBytes, INVENTORY_TTL_SECONDS);

            // 锁定计数器清零
            byte[] lockedKeyBytes = RedisKeys.sessionLocked(sessionId).getBytes(StandardCharsets.UTF_8);
            connection.set(lockedKeyBytes, "0".getBytes(StandardCharsets.UTF_8));
            connection.expire(lockedKeyBytes, INVENTORY_TTL_SECONDS);

            // 每座 Hash 信息(选座/派座都用得到)
            for (Seat seat : seats) {
                String seatInfoKey = RedisKeys.seatInfo(seat.getId());
                byte[] seatInfoKeyBytes = seatInfoKey.getBytes(StandardCharsets.UTF_8);
                Map<byte[], byte[]> seatInfoMap = new HashMap<>();
                seatInfoMap.put("row".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(seat.getRowNo()).getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("col".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(seat.getColNo()).getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("type".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(seat.getType()).getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("areaId".getBytes(StandardCharsets.UTF_8),
                        (seat.getAreaId() != null ? seat.getAreaId() : "").getBytes(StandardCharsets.UTF_8));
                connection.hMSet(seatInfoKeyBytes, seatInfoMap);
                connection.expire(seatInfoKeyBytes, INVENTORY_TTL_SECONDS);
            }

            // 区域价格 + 售卖模式配置 Hash(reserveStock 路径直接读 Redis,避免每次 SQL)
            for (SeatArea area : areas) {
                String areaKey = RedisKeys.seatAreaPrice(sessionId, area.getAreaId());
                byte[] areaKeyBytes = areaKey.getBytes(StandardCharsets.UTF_8);
                Map<byte[], byte[]> areaInfoMap = new HashMap<>();
                areaInfoMap.put("price".getBytes(StandardCharsets.UTF_8),
                        area.getPrice().toPlainString().getBytes(StandardCharsets.UTF_8));
                areaInfoMap.put("originPrice".getBytes(StandardCharsets.UTF_8),
                        area.getOriginPrice().toPlainString().getBytes(StandardCharsets.UTF_8));
                areaInfoMap.put("saleMode".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(area.getSaleMode() != null ? area.getSaleMode() : 1)
                                .getBytes(StandardCharsets.UTF_8));
                areaInfoMap.put("singleTotal".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(area.getSingleTotal() != null ? area.getSingleTotal() : 0)
                                .getBytes(StandardCharsets.UTF_8));
                areaInfoMap.put("coupleTotal".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(area.getCoupleTotal() != null ? area.getCoupleTotal() : 0)
                                .getBytes(StandardCharsets.UTF_8));
                connection.hMSet(areaKeyBytes, areaInfoMap);
                connection.expire(areaKeyBytes, INVENTORY_TTL_SECONDS);
            }

            // 派座区域:库存计数器 + 池子 ZSET
            for (SeatArea area : areas) {
                if (area.getSaleMode() == null || area.getSaleMode() != 2) continue;

                String areaId = area.getAreaId();
                List<Seat> singles = singleByArea.getOrDefault(areaId, Collections.emptyList());
                List<String> couples = coupleMembersByArea.getOrDefault(areaId, Collections.emptyList());

                // 单座计数器 + 池
                byte[] stockSingleBytes = RedisKeys.areaStockSingle(sessionId, areaId)
                        .getBytes(StandardCharsets.UTF_8);
                connection.set(stockSingleBytes,
                        String.valueOf(singles.size()).getBytes(StandardCharsets.UTF_8));
                connection.expire(stockSingleBytes, INVENTORY_TTL_SECONDS);

                if (!singles.isEmpty()) {
                    byte[] poolSingleBytes = RedisKeys.areaPoolSingle(sessionId, areaId)
                            .getBytes(StandardCharsets.UTF_8);
                    for (Seat s : singles) {
                        double score = (double) s.getRowNo() * 100000 + s.getColNo();
                        connection.zAdd(poolSingleBytes, score,
                                String.valueOf(s.getId()).getBytes(StandardCharsets.UTF_8));
                    }
                    connection.expire(poolSingleBytes, INVENTORY_TTL_SECONDS);
                }

                // 情侣对计数器 + 池
                byte[] stockCoupleBytes = RedisKeys.areaStockCouple(sessionId, areaId)
                        .getBytes(StandardCharsets.UTF_8);
                connection.set(stockCoupleBytes,
                        String.valueOf(couples.size()).getBytes(StandardCharsets.UTF_8));
                connection.expire(stockCoupleBytes, INVENTORY_TTL_SECONDS);

                if (!couples.isEmpty()) {
                    byte[] poolCoupleBytes = RedisKeys.areaPoolCouple(sessionId, areaId)
                            .getBytes(StandardCharsets.UTF_8);
                    for (String m : couples) {
                        // member 形如 "leftId:rightId",取 leftSeat 计算 score
                        long leftId = Long.parseLong(m.substring(0, m.indexOf(':')));
                        Seat left = seatIndex.get(leftId);
                        double score = (double) left.getRowNo() * 100000 + left.getColNo();
                        connection.zAdd(poolCoupleBytes, score, m.getBytes(StandardCharsets.UTF_8));
                    }
                    connection.expire(poolCoupleBytes, INVENTORY_TTL_SECONDS);
                }
            }
            return null;
        });
    }

    /**
     * 暴露 warmup 计算出的区域 single/couple 统计,供调用方回写 DB。
     */
    public Map<String, int[]> computeAreaTotals(List<Seat> seats) {
        Map<String, int[]> areaTotals = new HashMap<>();
        Map<Long, Seat> seatIndex = new HashMap<>();
        for (Seat s : seats) seatIndex.put(s.getId(), s);
        Set<Long> coupleConsumed = new HashSet<>();

        for (Seat s : seats) {
            String areaId = s.getAreaId() == null ? "" : s.getAreaId();
            int[] totals = areaTotals.computeIfAbsent(areaId, k -> new int[]{0, 0});
            if (s.getType() != null && s.getType() == 1) {
                totals[0]++;
            } else if (s.getType() != null && (s.getType() == 2 || s.getType() == 3)) {
                if (coupleConsumed.contains(s.getId())) continue;
                Long pairId = s.getPairSeatId();
                if (pairId == null) continue;
                Seat pair = seatIndex.get(pairId);
                if (pair == null) continue;
                coupleConsumed.add(s.getId());
                coupleConsumed.add(pair.getId());
                totals[1]++;
            }
        }
        return areaTotals;
    }

    public Set<String> getAvailableSeatIds(long sessionId) {
        return redisTemplate.opsForSet().members(RedisKeys.sessionSeats(sessionId));
    }

    public long getAvailableCount(long sessionId) {
        byte[] sessionKeyBytes = RedisKeys.sessionSeats(sessionId).getBytes(StandardCharsets.UTF_8);
        byte[] lockedKeyBytes  = RedisKeys.sessionLocked(sessionId).getBytes(StandardCharsets.UTF_8);
        List<Object> results = redisTemplate.executePipelined((RedisConnection conn) -> {
            conn.sCard(sessionKeyBytes);
            conn.get(lockedKeyBytes);
            return null;
        });
        long total  = results.get(0) instanceof Long  ? (Long) results.get(0) : 0L;
        String lockedStr = (String) results.get(1);
        long locked = lockedStr != null ? Long.parseLong(lockedStr) : 0L;
        return Math.max(0L, total - locked);
    }

    public boolean lockSeat(long sessionId, long seatId, String userId, long ttlSeconds) {
        String lockKey = RedisKeys.seatLock(sessionId, seatId);
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 批量原子锁座(选座模式)
     */
    public boolean batchLockSeats(long sessionId, List<Long> seatIds, String userId, long ttlSeconds) {
        List<String> argv = new ArrayList<>();
        argv.add(String.valueOf(sessionId));
        argv.add(userId);
        argv.add(String.valueOf(ttlSeconds));
        for (Long seatId : seatIds) argv.add(String.valueOf(seatId));

        Long result = redisTemplate.execute(
                BATCH_LOCK_SCRIPT,
                Collections.emptyList(),
                (Object[]) argv.toArray(new String[0])
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 释放座位锁并将座位重新加入可售集合(选座模式 + 派座模式的"全局集合"部分共用)
     */
    public void releaseSeat(long sessionId, long seatId) {
        String lockKey    = RedisKeys.seatLock(sessionId, seatId);
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        String lockedKey  = RedisKeys.sessionLocked(sessionId);

        redisTemplate.execute(
                RELEASE_SEAT_SCRIPT,
                Arrays.asList(lockKey, sessionKey, lockedKey),
                String.valueOf(seatId)
        );
    }

    /**
     * 消费座位(支付成功后调用,选座模式)
     */
    public boolean consumeSeat(long sessionId, long seatId, String userId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        String lockKey    = RedisKeys.seatLock(sessionId, seatId);
        String lockedKey  = RedisKeys.sessionLocked(sessionId);

        Long result = redisTemplate.execute(
                CONSUME_SEAT_SCRIPT,
                Arrays.asList(sessionKey, lockKey, lockedKey),
                String.valueOf(seatId), userId
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 派座模式:消费已派出的座位(派座流程在 ZPOPMIN 时已把座位从池里拿出,
     * 这里只把全场 session:seats 集合里同步 SREM,并把 seat:lock 持有者改写为该用户)。
     * 设计上派座时不写 seat:lock(直接 ZPOPMIN 已经"持有"),所以这里仅做集合 SREM。
     */
    public void consumeAllocatedSeat(long sessionId, long seatId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        redisTemplate.opsForSet().remove(sessionKey, String.valueOf(seatId));
    }

    /**
     * 派座模式 — 同步阶段:原子扣减区域库存(不取池)。
     *
     * 抢购洪峰时所有请求都先打这里,失败立即返回,不去碰池子,极低开销。
     *
     * @return true=扣减成功 / false=库存不足
     */
    public boolean reserveAreaStock(long sessionId, String areaId, int ticketType, int quantity) {
        String stockKey = ticketType == 1
                ? RedisKeys.areaStockSingle(sessionId, areaId)
                : RedisKeys.areaStockCouple(sessionId, areaId);
        Long result = redisTemplate.execute(
                RESERVE_STOCK_SCRIPT,
                Collections.singletonList(stockKey),
                String.valueOf(quantity)
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 派座模式 — 异步阶段:从池中 ZPOPMIN N 个 member。
     *
     * 配合 {@link #reserveAreaStock} 使用:同步先扣库存,异步从池里取座。
     * 若池子数 < N(数据不一致,概率极低),Lua 会把已弹的塞回去并返回空 list,
     * 由调用方负责回滚库存。
     *
     * @return 偶数长度的列表 [member1, score1, member2, score2, ...];空 list 表示池子异常
     */
    @SuppressWarnings("unchecked")
    public List<String> popFromPool(long sessionId, String areaId, int ticketType, int quantity) {
        String poolKey = ticketType == 1
                ? RedisKeys.areaPoolSingle(sessionId, areaId)
                : RedisKeys.areaPoolCouple(sessionId, areaId);
        Object result = redisTemplate.execute(
                POP_POOL_SCRIPT,
                Collections.singletonList(poolKey),
                String.valueOf(quantity)
        );
        if (result == null) return Collections.emptyList();
        List<Object> raw = (List<Object>) result;
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(String.valueOf(o));
        return out;
    }

    /**
     * 派座模式:把派出去的座位归还到池(退款/取消/派座失败回滚均会调用)。
     * 库存计数器 INCRBY + ZADD 回池,Lua 内原子完成。
     */
    public void releaseToPool(long sessionId, String areaId, int ticketType,
                              Map<String, Double> members) {
        if (members == null || members.isEmpty()) return;
        String stockKey = ticketType == 1
                ? RedisKeys.areaStockSingle(sessionId, areaId)
                : RedisKeys.areaStockCouple(sessionId, areaId);
        String poolKey = ticketType == 1
                ? RedisKeys.areaPoolSingle(sessionId, areaId)
                : RedisKeys.areaPoolCouple(sessionId, areaId);

        List<String> argv = new ArrayList<>();
        argv.add(String.valueOf(members.size()));
        for (Map.Entry<String, Double> e : members.entrySet()) {
            argv.add(e.getKey());
            argv.add(String.valueOf(e.getValue()));
        }
        redisTemplate.execute(
                RELEASE_TO_POOL_SCRIPT,
                Arrays.asList(stockKey, poolKey),
                (Object[]) argv.toArray(new String[0])
        );
    }

    /**
     * 仅回滚库存计数(用于"扣库存成功但池里取座失败"的回滚)。
     */
    public void rollbackAreaStock(long sessionId, String areaId, int ticketType, int quantity) {
        String stockKey = ticketType == 1
                ? RedisKeys.areaStockSingle(sessionId, areaId)
                : RedisKeys.areaStockCouple(sessionId, areaId);
        redisTemplate.opsForValue().increment(stockKey, quantity);
    }

    /**
     * 查询区域剩余库存(单座/情侣对) — 前端展示用
     */
    public long getAreaStock(long sessionId, String areaId, int ticketType) {
        String stockKey = ticketType == 1
                ? RedisKeys.areaStockSingle(sessionId, areaId)
                : RedisKeys.areaStockCouple(sessionId, areaId);
        String v = redisTemplate.opsForValue().get(stockKey);
        return v == null ? 0L : Long.parseLong(v);
    }

    public Map<Object, Object> getSeatInfo(long seatId) {
        return redisTemplate.opsForHash().entries(RedisKeys.seatInfo(seatId));
    }

    /**
     * 批量查询座位实时状态(Pipeline SISMEMBER)
     */
    public Map<Long, Integer> batchGetSeatStatus(long sessionId, List<Long> seatIds) {
        if (seatIds.isEmpty()) return new HashMap<>();

        byte[] sessionKeyBytes = RedisKeys.sessionSeats(sessionId).getBytes(StandardCharsets.UTF_8);
        List<Object> isMemberResults = redisTemplate.executePipelined((RedisConnection conn) -> {
            for (Long seatId : seatIds) {
                conn.sIsMember(sessionKeyBytes,
                        String.valueOf(seatId).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        List<Long> inAvailable = new ArrayList<>();
        for (int i = 0; i < seatIds.size(); i++) {
            Object r = isMemberResults.get(i);
            if (Boolean.TRUE.equals(r) || (r instanceof Long && (Long) r > 0)) {
                inAvailable.add(seatIds.get(i));
            }
        }

        Map<Long, Boolean> lockedMap = new HashMap<>();
        if (!inAvailable.isEmpty()) {
            List<Object> existsResults = redisTemplate.executePipelined((RedisConnection conn) -> {
                for (Long seatId : inAvailable) {
                    conn.exists(RedisKeys.seatLock(sessionId, seatId)
                            .getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            for (int i = 0; i < inAvailable.size(); i++) {
                Object r = existsResults.get(i);
                boolean locked = Boolean.TRUE.equals(r) || (r instanceof Long && (Long) r > 0);
                lockedMap.put(inAvailable.get(i), locked);
            }
        }

        Set<Long> availableSet = new HashSet<>(inAvailable);
        Map<Long, Integer> statusMap = new HashMap<>();
        for (Long seatId : seatIds) {
            if (!availableSet.contains(seatId)) {
                statusMap.put(seatId, 2);
            } else if (Boolean.TRUE.equals(lockedMap.get(seatId))) {
                statusMap.put(seatId, 1);
            } else {
                statusMap.put(seatId, 0);
            }
        }
        return statusMap;
    }

    public String getAreaPrice(long sessionId, String areaId) {
        Object price = redisTemplate.opsForHash().get(RedisKeys.seatAreaPrice(sessionId, areaId), "price");
        return price != null ? (String) price : null;
    }

    /**
     * 派座路径校验用 — 从 Redis 一次拿出 saleMode/singleTotal/coupleTotal。
     * 未命中返回 null,调用方应回退到 DB 查询并自愈地写回。
     *
     * @return 三个字段都拿到则返回非 null Map,否则返回 null
     */
    public AreaSaleConfig getAreaSaleConfig(long sessionId, String areaId) {
        List<Object> values = redisTemplate.opsForHash().multiGet(
                RedisKeys.seatAreaPrice(sessionId, areaId),
                Arrays.asList("saleMode", "singleTotal", "coupleTotal"));
        if (values == null || values.size() < 3
                || values.get(0) == null) {
            return null;
        }
        try {
            int saleMode    = Integer.parseInt(String.valueOf(values.get(0)));
            int singleTotal = values.get(1) != null ? Integer.parseInt(String.valueOf(values.get(1))) : 0;
            int coupleTotal = values.get(2) != null ? Integer.parseInt(String.valueOf(values.get(2))) : 0;
            return new AreaSaleConfig(saleMode, singleTotal, coupleTotal);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 销售中价格/区域配置增量刷新 — 只更新 seatAreaPrice Hash 的字段,
     * 不动 session:seats SET / area:pool ZSET / area:stock 计数器。
     *
     * <p>这是 {@link #warmup} 的安全替代方案,用于场次已销售中(status=1)时调整价格/配置:
     * warmup 会无脑重置 stock/pool/SET → 已售座位被复活 → 真实超卖。
     * 这里只刷新展示性字段,保留正在进行的销售状态。
     */
    public void refreshAreaConfig(long sessionId, List<SeatArea> areas) {
        if (areas == null || areas.isEmpty()) return;
        for (SeatArea area : areas) {
            String key = RedisKeys.seatAreaPrice(sessionId, area.getAreaId());
            Map<String, String> fields = new HashMap<>();
            fields.put("price", area.getPrice().toPlainString());
            fields.put("originPrice", area.getOriginPrice().toPlainString());
            fields.put("saleMode",
                    String.valueOf(area.getSaleMode() != null ? area.getSaleMode() : 1));
            fields.put("singleTotal",
                    String.valueOf(area.getSingleTotal() != null ? area.getSingleTotal() : 0));
            fields.put("coupleTotal",
                    String.valueOf(area.getCoupleTotal() != null ? area.getCoupleTotal() : 0));
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.expire(key, INVENTORY_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 自愈写回:DB 查到 area 后把 saleMode/totals 补到 Redis,避免下次仍 miss。
     * 不写 price/originPrice,因为价格不允许通过这条路径覆盖(以 SeatAreaService.saveAreas 的 warmup 为准)。
     */
    public void writeAreaSaleConfigBack(long sessionId, String areaId, SeatArea area) {
        String key = RedisKeys.seatAreaPrice(sessionId, areaId);
        Map<String, String> fields = new HashMap<>();
        fields.put("saleMode",
                String.valueOf(area.getSaleMode() != null ? area.getSaleMode() : 1));
        fields.put("singleTotal",
                String.valueOf(area.getSingleTotal() != null ? area.getSingleTotal() : 0));
        fields.put("coupleTotal",
                String.valueOf(area.getCoupleTotal() != null ? area.getCoupleTotal() : 0));
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, INVENTORY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** 区域售卖配置:saleMode / singleTotal / coupleTotal */
    public static class AreaSaleConfig {
        public final int saleMode;
        public final int singleTotal;
        public final int coupleTotal;
        public AreaSaleConfig(int saleMode, int singleTotal, int coupleTotal) {
            this.saleMode = saleMode;
            this.singleTotal = singleTotal;
            this.coupleTotal = coupleTotal;
        }
    }
}
