package com.ticket.user.init;

import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.producer.RefundProducer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 僵尸退款补偿任务:扫描卡在 status=3(退款中)且 update_time 超过阈值的订单,重新投递 RefundEvent.
 *
 * 触发原因(参考 OrderService.doRefund):
 *   - DB 已将 status 改为 3 + MQ 发送成功后,进程崩溃,RefundConsumer 收不到消息或未完成处理
 *   - 消费者业务异常重试用尽后被丢弃
 *
 * 用 Redisson 分布式锁保证 cluster 多副本中只有一个实例执行扫描。
 */
@Slf4j
@Component
public class RefundReconciler {

    /** 退款卡死阈值:订单 update_time 早于(now - STUCK_MINUTES) 视为僵尸 */
    private static final int STUCK_MINUTES = 10;
    /** 单次扫描批次上限 */
    private static final int BATCH_LIMIT = 100;
    /** 分布式锁名称 */
    private static final String LOCK_NAME = "refund:reconciler:lock";
    /** 锁租约时长(秒) */
    private static final long LOCK_LEASE_SECONDS = 50;
    /** 订单状态:退款中 */
    private static final int STATUS_REFUNDING = 3;
    /** 票券状态:未使用(可退) */
    private static final int TICKET_STATUS_UNUSED = 0;

    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final RefundProducer refundProducer;
    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public RefundReconciler(OrderMapper orderMapper,
                            TicketMapper ticketMapper,
                            RefundProducer refundProducer,
                            RedissonClient redissonClient,
                            MeterRegistry meterRegistry) {
        this.orderMapper = orderMapper;
        this.ticketMapper = ticketMapper;
        this.refundProducer = refundProducer;
        this.redissonClient = redissonClient;
        this.meterRegistry = meterRegistry;
    }

    /** 每分钟执行一次 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scan() {
        RLock lock = redissonClient.getLock(LOCK_NAME);
        boolean acquired = false;
        try {
            // 非阻塞尝试锁,拿不到说明已有副本在执行,本次跳过
            acquired = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                return;
            }
            doScan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("退款补偿扫描异常", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doScan() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_MINUTES);
        List<Order> stuck = orderMapper.selectStuckOrders(STATUS_REFUNDING, threshold, BATCH_LIMIT);
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("发现僵尸退款订单 {} 条,开始补偿重投 MQ", stuck.size());
        for (Order order : stuck) {
            try {
                // 当前可退座位 = 未使用且未退款的票券对应的座位
                List<Long> seatIds = ticketMapper.selectByOrderId(order.getId()).stream()
                        .filter(t -> t.getStatus() == TICKET_STATUS_UNUSED)
                        .map(Ticket::getSeatId)
                        .collect(Collectors.toList());
                if (seatIds.isEmpty()) {
                    // 需人工介入的卡单 — 该指标 > 0 必须告警
                    meterRegistry.counter("refund.reconcile.manual").increment();
                    log.warn("订单已无可退座位但仍卡在退款中,需人工介入,orderId={}", order.getId());
                    continue;
                }
                refundProducer.sendRefund(order.getId(), seatIds);
                meterRegistry.counter("refund.reconcile.resend").increment();
                log.info("退款补偿:重投 MQ orderId={} seats={}", order.getId(), seatIds);
            } catch (Exception e) {
                log.error("退款补偿重投失败 orderId={}", order.getId(), e);
            }
        }
    }
}
