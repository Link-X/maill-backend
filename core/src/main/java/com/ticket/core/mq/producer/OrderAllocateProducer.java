package com.ticket.core.mq.producer;

import com.ticket.core.domain.dto.OrderAllocateMessage;
import com.ticket.core.mq.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderAllocateProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderAllocateProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendAllocateMessage(OrderAllocateMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_ALLOCATE_EXCHANGE,
                RabbitMQConfig.ORDER_ALLOCATE_ROUTING_KEY,
                message
        );
        log.debug("发送异步派座建单消息, orderNo={}", message.getOrderNo());
    }
}
