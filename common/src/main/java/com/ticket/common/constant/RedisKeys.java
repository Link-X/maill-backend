package com.ticket.common.constant;

/**
 * Redis Key 常量工具类.
 *
 * 统一管理所有 Redis 操作的 key 格式,避免散落在各处硬编码.
 * 每个方法对应一种 key 模式:
 *   sessionSeats      — Set,存储场次下所有可售座位 ID
 *   seatInfo          — Hash,存储座位详细信息(排号/列号/价格等)
 *   seatLock          — String,座位锁(NX+EXPIRE 实现分布式锁)
 *   sessionPurchase   — String,用户在某场次的已购数量
 *   ticketOrderQueue  — Redis Stream,订单削峰队列
 *   userRateLimit     — String,用户限流计数
 *   sessionLock       — String,场次级分布式锁
 */
public class RedisKeys {

    private RedisKeys() {}

    public static String sessionSeats(long sessionId) {
        return "session:seats:" + sessionId;
    }

    public static String seatInfo(long seatId) {
        return "seat:info:" + seatId;
    }

    public static String seatLock(long sessionId, long seatId) {
        return "seat:lock:" + sessionId + ":" + seatId;
    }

    public static String sessionPurchase(long sessionId, long userId) {
        return "session:purchase:" + sessionId + ":" + userId;
    }

    public static String ticketOrderQueue(long sessionId) {
        return "ticket:order:queue:" + sessionId;
    }

    public static String userRateLimit(long userId) {
        return "user:rate:limit:" + userId;
    }

    public static String sessionLock(long sessionId) {
        return "session:lock:" + sessionId;
    }

    /** 场次当前锁定中（结账中）的座位数量计数器 */
    public static String sessionLocked(long sessionId) {
        return "session:locked:" + sessionId;
    }

    /** 场次内某区域的价格信息 Hash（字段: price, originPrice） */
    public static String seatAreaPrice(long sessionId, String areaId) {
        return "session:area:price:" + sessionId + ":" + areaId;
    }

    /** requestId（Redis Stream record ID）到订单号的映射 */
    public static String orderRequest(String requestId) {
        return "order:request:" + requestId;
    }

    // 限流 key：滑动窗口算法,窗口内所有请求共用同一 key,
    // 时间维度由 ZSET score 体现,不再需要 windowSecond 时间桶后缀。

    public static String rateLimitGlobal(String methodKey) {
        return "rate:global:" + methodKey;
    }

    public static String rateLimitUser(long userId, String methodKey) {
        return "rate:user:" + userId + ":" + methodKey;
    }

    public static String rateLimitIp(String ip, String methodKey) {
        return "rate:ip:" + ip + ":" + methodKey;
    }

    public static String blacklistUser(long userId) {
        return "blacklist:user:" + userId;
    }

    public static String blacklistIp(String ip) {
        return "blacklist:ip:" + ip;
    }

    /** 资讯浏览数累计 Hash：field=articleId, value=未回写的增量 */
    public static String articleViewBuffer() {
        return "article:view:buffer";
    }

    /**
     * 异步建单临时状态:value 取 "PROCESSING" 或 "FAILED:<reason>",TTL 60s 兜底自动清。
     * 消费者建单成功后会主动 DELETE 此 key,前端按 orderNo 查 DB 命中后即为成功。
     */
    public static String orderCreatePending(String orderNo) {
        return "order:create:pending:" + orderNo;
    }

    // ========== 派座(sale_mode=2)相关 ==========

    /**
     * 区域单座剩余张数(STRING,原子 DECRBY/INCRBY)。
     * 与情侣池物理隔离,情侣座永远不会出现在此计数器对应的池子里。
     */
    public static String areaStockSingle(long sessionId, String areaId) {
        return "area:stock:single:" + sessionId + ":" + areaId;
    }

    /**
     * 区域情侣对剩余对数(STRING,原子 DECRBY/INCRBY)。一对 = 2 个 seat。
     */
    public static String areaStockCouple(long sessionId, String areaId) {
        return "area:stock:couple:" + sessionId + ":" + areaId;
    }

    /**
     * 区域单座池(ZSET, score=rowNo*100000+colNo, member=seatId)。
     * 按 score 升序 = 按排号/列号升序,方便取"靠前/连续"的座位。
     */
    public static String areaPoolSingle(long sessionId, String areaId) {
        return "area:pool:single:" + sessionId + ":" + areaId;
    }

    /**
     * 区域情侣对池(ZSET, score=rowNo*100000+min(colNo), member="leftSeatId:rightSeatId")。
     * 一个 member 就是"一对",派对操作天然原子,不可能拆散。
     */
    public static String areaPoolCouple(long sessionId, String areaId) {
        return "area:pool:couple:" + sessionId + ":" + areaId;
    }
}
