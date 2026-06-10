package com.ticket.core.scheduler;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 库存对账调度器。
 *
 * <p>Redis 是在售场次库存的唯一真相源(session:seats SET / area:stock 计数器 / area:pool ZSET),
 * 但 Redis 数据丢失(AOF 损坏、误删 key、容器重建)后没有任何自动发现机制 —
 * 表现为所有购票请求返回"库存不足",直到有人想起来手动执行 warmup。
 *
 * <p>本调度器每 5 分钟对所有 status=1(销售中)场次做结构性核对:
 * <ul>
 *   <li>含选座区(sale_mode!=2)的场次:session:seats SET 必须存在</li>
 *   <li>派座区(sale_mode=2):对应的 single/couple 库存计数器必须存在且非负</li>
 *   <li>派座区:池内座位数少于库存计数(pool &lt; stock)说明池数据丢失,后续派座必然失败</li>
 * </ul>
 *
 * <p>发现异常只做 ERROR 日志 + {@code inventory.reconcile.anomaly} 指标告警,
 * 刻意不自动重建:销售中重放 warmup 会无脑重置 stock/pool/SET,把已售座位复活造成真实超卖
 * (参见 SeatAreaService 对 warmup/refreshAreaConfig 的区分)。
 * 数据确认丢失后,应由管理员核对订单数据后调用 admin 的 warmup 接口恢复。
 */
@Slf4j
@Component
public class InventoryReconcileScheduler {

    /** 单次核对的在售场次上限(正常业务远达不到,防御性限制) */
    private static final int SESSION_LIMIT = 500;

    private static final int STATUS_ON_SALE = 1;

    private final ShowSessionMapper showSessionMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public InventoryReconcileScheduler(ShowSessionMapper showSessionMapper,
                                       SeatAreaMapper seatAreaMapper,
                                       StringRedisTemplate redisTemplate,
                                       MeterRegistry meterRegistry) {
        this.showSessionMapper = showSessionMapper;
        this.seatAreaMapper = seatAreaMapper;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "15 */5 * * * ?")
    @SchedulerLock(name = "InventoryReconcileScheduler.tick",
            lockAtLeastFor = "PT1M", lockAtMostFor = "PT4M")
    public void tick() {
        List<ShowSession> onSale;
        try {
            onSale = showSessionMapper.selectByCondition(null, STATUS_ON_SALE, null, null, 0, SESSION_LIMIT);
        } catch (Exception e) {
            log.error("[INV-RECONCILE] 查询在售场次失败", e);
            return;
        }
        for (ShowSession session : onSale) {
            try {
                reconcileSession(session.getId());
            } catch (Exception e) {
                log.error("[INV-RECONCILE] 场次核对异常 sessionId={}", session.getId(), e);
            }
        }
    }

    private void reconcileSession(long sessionId) {
        List<SeatArea> areas = seatAreaMapper.selectBySessionId(sessionId);
        if (areas.isEmpty()) {
            return;
        }

        boolean hasSelectionArea = areas.stream()
                .anyMatch(a -> a.getSaleMode() == null || a.getSaleMode() != 2);
        if (hasSelectionArea
                && !Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.sessionSeats(sessionId)))) {
            anomaly("session_set_missing", sessionId, null,
                    "在售场次的 session:seats SET 不存在,选座购票已不可用,需管理员核对后 warmup 恢复");
        }

        for (SeatArea area : areas) {
            if (area.getSaleMode() == null || area.getSaleMode() != 2) {
                continue;
            }
            if (area.getSingleTotal() != null && area.getSingleTotal() > 0) {
                checkStockAndPool(sessionId, area.getAreaId(),
                        RedisKeys.areaStockSingle(sessionId, area.getAreaId()),
                        RedisKeys.areaPoolSingle(sessionId, area.getAreaId()));
            }
            if (area.getCoupleTotal() != null && area.getCoupleTotal() > 0) {
                checkStockAndPool(sessionId, area.getAreaId(),
                        RedisKeys.areaStockCouple(sessionId, area.getAreaId()),
                        RedisKeys.areaPoolCouple(sessionId, area.getAreaId()));
            }
        }
    }

    private void checkStockAndPool(long sessionId, String areaId, String stockKey, String poolKey) {
        String stockValue = redisTemplate.opsForValue().get(stockKey);
        if (stockValue == null) {
            anomaly("stock_missing", sessionId, areaId,
                    "派座库存计数器不存在(" + stockKey + "),该区域购票已不可用");
            return;
        }
        long stock;
        try {
            stock = Long.parseLong(stockValue);
        } catch (NumberFormatException e) {
            anomaly("stock_corrupt", sessionId, areaId, "库存计数器值非法: " + stockValue);
            return;
        }
        if (stock < 0) {
            anomaly("stock_negative", sessionId, areaId, "库存计数器为负数: " + stock);
        }

        Long poolSize = redisTemplate.opsForZSet().zCard(poolKey);
        long pool = poolSize == null ? 0 : poolSize;
        // 正常时序里 stock 先扣、pool 后弹,pool >= stock 恒成立;
        // pool < stock 说明池 member 丢失,库存计数还有余量但实际派不出座位。
        // 扣减与弹出之间存在短暂窗口,偶发一次可能是在途请求,持续出现才需要处理。
        if (pool < stock) {
            meterRegistry.counter("inventory.reconcile.anomaly",
                    "type", "pool_lt_stock").increment();
            log.warn("[INV-RECONCILE] 池座位数小于库存计数 sessionId={} areaId={} pool={} stock={},持续出现需人工核对",
                    sessionId, areaId, pool, stock);
        }
    }

    private void anomaly(String type, long sessionId, String areaId, String detail) {
        meterRegistry.counter("inventory.reconcile.anomaly", "type", type).increment();
        log.error("[INV-RECONCILE] 库存异常 type={} sessionId={} areaId={} — {}",
                type, sessionId, areaId, detail);
    }
}
