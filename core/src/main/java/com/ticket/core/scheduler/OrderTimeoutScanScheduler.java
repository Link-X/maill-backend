package com.ticket.core.scheduler;

import com.ticket.core.domain.entity.Order;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.service.OrderCommandService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时兜底扫描器。
 *
 * <p>下单链路:同步建单 + 设 expire_time(下单时刻 +5min) + afterCommit 发 MQ 延迟消息(5min TTL)。
 * 正常路径下,延迟消息到期后 OrderTimeoutConsumer 调 cancelByTimeout 取消订单。
 *
 * <p>但 afterCommit 在事务 commit 之后才执行,如果此时 MQ 发送失败(网络抖动 / RabbitMQ 短暂不可用),
 * 异常会被 Spring 吞掉(事务已 commit 无法回滚),订单永远不会被自动取消 → 座位永久锁定。
 *
 * <p>本扫描器作为兜底:每分钟扫一次 status=0 且 expire_time 早于 (now - 缓冲) 的订单,
 * 调用 cancelByTimeout 触发取消。cancelByTimeout 内部 CAS 保证不会与 MQ consumer 重复取消。
 *
 * <p>缓冲设计 ({@link #BUFFER_SECONDS}):给 MQ consumer 一段窗口处理正常路径,避免与 consumer 抢答
 * 造成不必要的 DB 查询;同时也避免在过期那一瞬间扫到 MQ 还在路上的订单。
 */
@Slf4j
@Component
public class OrderTimeoutScanScheduler {

    /** 超过 expire_time 多少秒还没取消才触发兜底取消(让 MQ 延迟消息有充分时间执行) */
    private static final long BUFFER_SECONDS = 60;

    /** 单次扫描上限,防止积压 */
    private static final int BATCH_LIMIT = 200;

    private final OrderMapper orderMapper;
    private final OrderCommandService orderCommandService;

    public OrderTimeoutScanScheduler(OrderMapper orderMapper,
                                     OrderCommandService orderCommandService) {
        this.orderMapper = orderMapper;
        this.orderCommandService = orderCommandService;
    }

    @Scheduled(cron = "45 * * * * ?")
    @SchedulerLock(name = "OrderTimeoutScanScheduler.tick",
            lockAtLeastFor = "PT30S", lockAtMostFor = "PT55S")
    public void tick() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(BUFFER_SECONDS);
        List<Order> overdue;
        try {
            overdue = orderMapper.selectExpiredOrders(0, threshold, BATCH_LIMIT);
        } catch (Exception e) {
            log.error("[ORDER-TIMEOUT-SCAN] 扫描失败", e);
            return;
        }
        if (overdue.isEmpty()) return;

        log.info("[ORDER-TIMEOUT-SCAN] 扫到超时未取消订单 {} 个(MQ 兜底)", overdue.size());
        for (Order order : overdue) {
            try {
                // cancelByTimeout 内部 cancelWithReason 用 status=0 CAS,与 MQ consumer 并发安全
                orderCommandService.cancelByTimeout(order.getId());
            } catch (Exception e) {
                log.error("[ORDER-TIMEOUT-SCAN] 取消订单失败 orderId={},下次重试",
                        order.getId(), e);
            }
        }
    }
}
