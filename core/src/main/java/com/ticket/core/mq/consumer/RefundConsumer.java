package com.ticket.core.mq.consumer;

import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.PaymentMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.RefundEvent;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Ticket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RefundConsumer {

    /** 退款幂等键 TTL（小时）：覆盖 MQ 最长重投窗口,远大于业务处理耗时 */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final SeatMapper seatMapper;
    private final TicketMapper ticketMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;
    private final StringRedisTemplate redisTemplate;

    public RefundConsumer(OrderMapper orderMapper,
                          PaymentMapper paymentMapper,
                          SeatMapper seatMapper,
                          TicketMapper ticketMapper,
                          SeatInventoryService inventoryService,
                          PurchaseLimitService purchaseLimitService,
                          StringRedisTemplate redisTemplate) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.seatMapper = seatMapper;
        this.ticketMapper = ticketMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.REFUND_QUEUE)
    public void handleRefund(RefundEvent event) {
        Long orderId = event.getOrderId();
        List<Long> refundSeatIds = event.getRefundSeatIds();
        // 幂等键：orderId + 本次退款座位集合，部分退款多次触发时 key 不同;同一退款重试时 key 相同
        String idempotentKey = buildIdempotentKey(orderId, refundSeatIds);
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("退款消息重复消费,直接跳过。orderId={},seatIds={}", orderId, refundSeatIds);
            return;
        }
        log.info("处理退款，orderId={}，退款座位数={}", orderId, refundSeatIds.size());
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("退款订单不存在，orderId={}", orderId);
                return;
            }

            // 1. 更新支付记录状态为已退款(2)
            paymentMapper.updateStatusByOrderId(orderId, 2);

            // 2. Redis 座位恢复可售（initiateRefund 已同步执行，此处幂等兜底）
            for (Long seatId : refundSeatIds) {
                inventoryService.releaseSeat(order.getSessionId(), seatId);
            }

            // 3. MySQL 退款座位状态恢复为可售(0)
            if (!refundSeatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(refundSeatIds, 0);
            }

            // 4. 回滚限购计数（只按退款座位数回滚）
            purchaseLimitService.decrement(order.getSessionId(), order.getUserId(), refundSeatIds.size());

            // 5. 作废退款座位对应的票券（已核销票券保持原状）
            ticketMapper.invalidateBySeatIds(orderId, refundSeatIds);

            // 6. 判断全退(4)还是部分退款(5):基于"剩余可退票券数",支持多次部分退款累积到全退
            //    ticket.status: 0=未使用(可退), 1=已使用(不参与), 2=已退款
            //    invalidateBySeatIds 已把本次退款票券标记为 2,剩余 status=0 即可继续退款
            List<Ticket> tickets = ticketMapper.selectByOrderId(orderId);
            long remainingRefundable = tickets.stream().filter(t -> t.getStatus() == 0).count();
            int finalStatus = remainingRefundable == 0 ? 4 : 5;
            orderMapper.updateStatusFrom(orderId, 3, finalStatus);

            log.info("退款完成，orderId={}，退款座位={}，最终状态={}", orderId, refundSeatIds, finalStatus);
        } catch (Exception e) {
            // 业务失败时清除幂等键,允许 MQ 重试时重新处理
            redisTemplate.delete(idempotentKey);
            log.error("退款处理失败，orderId={}，原因：{}", orderId, e.getMessage(), e);
            throw e;
        }
    }

    private String buildIdempotentKey(Long orderId, List<Long> refundSeatIds) {
        // 复制后排序,避免不同顺序的同一批座位被识别为两次退款。
        // 直接拼接 seatId 而非 hashCode:32 位哈希存在碰撞可能,碰撞会让合法退款被误判为重复而永久跳过
        String seatPart = refundSeatIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return "refund:idempotent:" + orderId + ":" + seatPart;
    }
}
