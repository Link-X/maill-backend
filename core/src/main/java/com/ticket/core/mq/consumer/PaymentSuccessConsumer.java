package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import com.ticket.core.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PaymentSuccessConsumer {

    /** 幂等键 TTL 24 小时,覆盖 MQ 最长重投窗口 */
    private static final long IDEMPOTENT_TTL_HOURS = 24;

    private final TicketService ticketService;
    private final StringRedisTemplate redisTemplate;

    public PaymentSuccessConsumer(TicketService ticketService,
                                  StringRedisTemplate redisTemplate) {
        this.ticketService = ticketService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 票券生成消费者:加 SETNX 幂等键作为 TicketService 分布式锁的兜底防御。
     * 锁租约超时等极端情况下两个消费者都通过锁后,幂等键阻止双倍发票。
     */
    @RabbitListener(queues = RabbitMQConfig.TICKET_GENERATE_QUEUE)
    public void generateTickets(PaymentSuccessEvent event) {
        String idempotentKey = "ticket:generate:idempotent:" + event.getOrderId();
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("票券生成消息重复消费,跳过。orderNo={}", event.getOrderNo());
            return;
        }
        log.info("生成票券，orderNo={}", event.getOrderNo());
        try {
            ticketService.generateTicketsForOrder(event.getOrderId(), event.getUserId());
        } catch (Exception e) {
            // 业务失败时清除幂等键,允许 MQ 重试
            redisTemplate.delete(idempotentKey);
            log.error("票券生成失败，orderNo={}", event.getOrderNo(), e);
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void sendNotification(PaymentSuccessEvent event) {
        // 预留：对接短信/推送服务
        log.info("预留通知，orderNo={}，userId={}", event.getOrderNo(), event.getUserId());
    }
}
