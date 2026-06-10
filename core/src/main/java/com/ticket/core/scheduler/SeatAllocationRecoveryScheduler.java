package com.ticket.core.scheduler;

import com.ticket.core.domain.entity.SeatAllocationTask;
import com.ticket.core.mapper.SeatAllocationTaskMapper;
import com.ticket.core.service.OrderCommandService;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatAllocationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 派座任务恢复扫描器。
 *
 * <p>派座下单流程:同步扣库存 + 写 task=PENDING + 发 MQ → 异步消费派座+建单。
 * 如果消费者挂掉/MQ 消息丢失/消费时进程崩溃,会留下 PENDING task。
 * 本调度器每分钟扫描超过 2 分钟仍 PENDING 的 task,根据 allocated_seats 判断回滚方式:
 * <ul>
 *   <li>{@code allocated_seats} 为空 — popFromPool 未执行,池没动,仅 INCRBY 还库存即可</li>
 *   <li>{@code allocated_seats} 非空 — 池已弹出,需要还库存 + 还池(走 releaseSeatsByMode)</li>
 * </ul>
 *
 * <p>多实例间通过 ShedLock 互斥;同一任务通过 CAS(updateStatusFrom)保证只回滚一次。
 */
@Slf4j
@Component
public class SeatAllocationRecoveryScheduler {

    /** 任务在 PENDING 状态超过此时长就视为消费失败,触发回滚 */
    private static final long TIMEOUT_SECONDS = 120;

    /** 单次扫描上限,避免一次性堆积过多回滚 */
    private static final int BATCH_LIMIT = 200;

    private final SeatAllocationTaskMapper taskMapper;
    private final SeatAllocationService allocationService;
    private final PurchaseLimitService purchaseLimitService;
    private final OrderCommandService orderCommandService;
    private final MeterRegistry meterRegistry;

    public SeatAllocationRecoveryScheduler(SeatAllocationTaskMapper taskMapper,
                                           SeatAllocationService allocationService,
                                           PurchaseLimitService purchaseLimitService,
                                           OrderCommandService orderCommandService,
                                           MeterRegistry meterRegistry) {
        this.taskMapper = taskMapper;
        this.allocationService = allocationService;
        this.purchaseLimitService = purchaseLimitService;
        this.orderCommandService = orderCommandService;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "30 * * * * ?")
    @SchedulerLock(name = "SeatAllocationRecoveryScheduler.tick",
            lockAtLeastFor = "PT20S", lockAtMostFor = "PT55S")
    public void tick() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(TIMEOUT_SECONDS);
        List<SeatAllocationTask> timeoutTasks;
        try {
            timeoutTasks = taskMapper.selectTimeoutPending(before, BATCH_LIMIT);
        } catch (Exception e) {
            log.error("派座任务超时扫描失败", e);
            return;
        }
        if (timeoutTasks.isEmpty()) return;

        log.info("[ALLOC-RECOVERY] 扫到超时 PENDING 任务 {} 个", timeoutTasks.size());

        for (SeatAllocationTask t : timeoutTasks) {
            // CAS:确认仍是 PENDING 才回滚,防止与消费者并发抢答
            int rolled = taskMapper.updateStatusFrom(t.getOrderNo(),
                    SeatAllocationTask.STATUS_PENDING,
                    SeatAllocationTask.STATUS_ROLLED_BACK,
                    null, "调度器超时回滚");
            if (rolled == 0) {
                continue; // 已被消费者抢先处理
            }
            try {
                rollbackOne(t);
                meterRegistry.counter("seat.allocation.recovery.rollback").increment();
                log.info("[ALLOC-RECOVERY] 回滚成功 orderNo={} sessionId={} areaId={} type={} qty={} hasAllocated={}",
                        t.getOrderNo(), t.getSessionId(), t.getAreaId(),
                        t.getTicketType(), t.getQuantity(),
                        t.getAllocatedSeats() != null && !t.getAllocatedSeats().isEmpty());
            } catch (Exception e) {
                // 已经 CAS 成 ROLLED_BACK,Redis 回滚失败要靠人工补偿 — 该指标 > 0 必须立即告警
                meterRegistry.counter("seat.allocation.recovery.manual").increment();
                log.error("[ALLOC-RECOVERY] 回滚失败 orderNo={},需人工补偿", t.getOrderNo(), e);
            }
        }
    }

    /**
     * 回滚单个 PENDING 任务。
     *  - allocated_seats 为空 → 池未动,只 INCR 库存
     *  - allocated_seats 非空 → 池已弹出 N 个 member,需还库存 + 还池(复用 releaseSeatsByMode 按 sale_mode 分流)
     */
    private void rollbackOne(SeatAllocationTask t) {
        int seatCount = t.getTicketType() == SeatAllocationTask.TICKET_TYPE_COUPLE
                ? t.getQuantity() * 2 : t.getQuantity();

        if (t.getAllocatedSeats() == null || t.getAllocatedSeats().isEmpty()) {
            // 只补 stock
            allocationService.rollbackStockOnly(t.getSessionId(), t.getAreaId(),
                    t.getTicketType(), t.getQuantity());
        } else {
            // 池已弹出 — 走 releaseSeatsByMode 还 stock + 还 pool(自动按 sale_mode 分流,
            // 派座区的 seat 会回到对应单座池/情侣对池)
            List<Long> seatIds = parseSeatIds(t.getAllocatedSeats());
            if (!seatIds.isEmpty()) {
                orderCommandService.releaseSeatsByMode(t.getSessionId(), seatIds);
            }
        }
        purchaseLimitService.decrement(t.getSessionId(), t.getUserId(), seatCount);
    }

    private List<Long> parseSeatIds(String csv) {
        List<Long> result = new ArrayList<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ex) {
                log.warn("[ALLOC-RECOVERY] 解析 allocated_seats 失败: {}", trimmed);
            }
        }
        return result;
    }
}
