package com.ticket.core.mq.producer;

import com.ticket.core.domain.dto.OrderCreateMessage;
import com.ticket.core.mq.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderCreateProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderCreateProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendCreateMessage(OrderCreateMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_CREATE_EXCHANGE,
                RabbitMQConfig.ORDER_CREATE_ROUTING_KEY,
                message
        );
        log.debug("发送异步建单消息, orderNo={}", message.getOrderNo());
    }
}
