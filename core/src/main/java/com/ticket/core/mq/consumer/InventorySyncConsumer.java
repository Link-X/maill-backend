package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.SeatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InventorySyncConsumer {

    /** 幂等键 TTL 24 小时,覆盖 MQ 最长重投窗口 */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final OrderItemMapper orderItemMapper;
    private final SeatMapper seatMapper;
    private final StringRedisTemplate redisTemplate;

    public InventorySyncConsumer(OrderItemMapper orderItemMapper,
                                 SeatMapper seatMapper,
                                 StringRedisTemplate redisTemplate) {
        this.orderItemMapper = orderItemMapper;
        this.seatMapper = seatMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 支付成功后将座位 DB 状态同步为已售。
     *
     * <p>必须幂等:若无幂等键,MQ 重投会把"已退款恢复可售(status=0)"的座位
     * 重新覆盖为已售(status=2),造成座位永久锁死。退款流程(RefundConsumer)
     * 在支付成功之后发生,重投的旧消息不允许再次执行。
     */
    @RabbitListener(queues = RabbitMQConfig.INVENTORY_SYNC_QUEUE)
    public void syncInventory(PaymentSuccessEvent event) {
        String idempotentKey = "inventory:sync:idempotent:" + event.getOrderId();
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("库存同步消息重复消费,直接跳过。orderNo={}", event.getOrderNo());
            return;
        }
        log.info("同步座位库存，orderNo={}", event.getOrderNo());
        try {
            List<Long> seatIds = orderItemMapper.selectByOrderId(event.getOrderId())
                    .stream()
                    .map(item -> item.getSeatId())
                    .collect(Collectors.toList());

            if (!seatIds.isEmpty()) {
                seatMapper.batchUpdateStatus(seatIds, 2); // 2=已售
            }
        } catch (Exception e) {
            // 失败清键,允许 MQ 重试时重新处理
            redisTemplate.delete(idempotentKey);
            log.error("座位库存同步失败，orderNo={}", event.getOrderNo(), e);
            throw e;
        }
    }
}
