package com.ticket.core.mq.consumer;

import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.PaymentSuccessEvent;
import com.ticket.core.service.TicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
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
        log.info("[TICKET-CONSUME] received orderId={} orderNo={} userId={}",
                event.getOrderId(), event.getOrderNo(), event.getUserId());
        String idempotentKey = "ticket:generate:idempotent:" + event.getOrderId();
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(firstTime)) {
            log.warn("[TICKET-CONSUME] duplicate, skip orderNo={}", event.getOrderNo());
            return;
        }
        try {
            List<Ticket> tickets = ticketService.generateTicketsForOrder(event.getOrderId(), event.getUserId());
            // 关键防御:即使 ticketService 没抛异常,若返回空列表也视为失败,清键 + 抛异常让 MQ 重试。
            // 之前的 bug:此处不检查返回值,空列表静默 ack,导致订单状态已支付但票不存在。
            if (tickets == null || tickets.isEmpty()) {
                redisTemplate.delete(idempotentKey);
                throw new IllegalStateException(
                        "票券生成返回空 orderId=" + event.getOrderId() + ",事务可能未就绪,触发重试");
            }
            log.info("[TICKET-CONSUME] done orderId={} ticketCount={}",
                    event.getOrderId(), tickets.size());
        } catch (Exception e) {
            redisTemplate.delete(idempotentKey);
            log.error("[TICKET-CONSUME] fail orderId={} orderNo={} err={}",
                    event.getOrderId(), event.getOrderNo(), e.getMessage(), e);
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void sendNotification(PaymentSuccessEvent event) {
        // 预留：对接短信/推送服务
        log.info("预留通知，orderNo={}，userId={}", event.getOrderNo(), event.getUserId());
    }
}
