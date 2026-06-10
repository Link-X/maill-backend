package com.ticket.core.mq.consumer;

import com.ticket.core.mq.config.RabbitMQConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 死信统一消费者:业务队列重试耗尽后的消息最终落到这里。
 *
 * <p>职责只有两件事:
 * <ul>
 *   <li>写 ERROR 日志,包含来源队列、死信原因和完整消息体 — 人工补偿时可直接取日志中的
 *       JSON 重发到原交换机</li>
 *   <li>递增 {@code mq.dlq.received} 指标(按来源队列打 tag),供 Prometheus 告警</li>
 * </ul>
 *
 * <p>刻意不做自动重发:能进 DLQ 说明已重试多次仍失败,大概率是数据或代码问题,
 * 自动重灌只会循环失败;资金链路(退款/出票)的死信必须人工介入核对后处理。
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    private final MeterRegistry meterRegistry;

    public DeadLetterConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = {
            RabbitMQConfig.ORDER_CANCEL_DLQ,
            RabbitMQConfig.ORDER_CREATE_DLQ,
            RabbitMQConfig.ORDER_ALLOCATE_DLQ,
            RabbitMQConfig.REFUND_DLQ,
            RabbitMQConfig.TICKET_GENERATE_DLQ,
            RabbitMQConfig.INVENTORY_SYNC_DLQ,
            RabbitMQConfig.NOTIFICATION_DLQ,
            RabbitMQConfig.SEARCH_SYNC_DLQ
    })
    public void handleDeadLetter(Message message) {
        String sourceQueue = firstDeathQueue(message);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.error("[MQ-DLQ] 业务消息重试耗尽进入死信! 来源队列={}, 死信原因={}, 消息体={}",
                sourceQueue, firstDeathReason(message), body);
        meterRegistry.counter("mq.dlq.received", "queue", sourceQueue).increment();
    }

    @SuppressWarnings("unchecked")
    private String firstDeathQueue(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object deaths = headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Object queue = ((Map<String, Object>) death).get("queue");
            if (queue != null) return queue.toString();
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private String firstDeathReason(Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        Object deaths = headers.get("x-death");
        if (deaths instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> death) {
            Object reason = ((Map<String, Object>) death).get("reason");
            if (reason != null) return reason.toString();
        }
        return "unknown";
    }
}
