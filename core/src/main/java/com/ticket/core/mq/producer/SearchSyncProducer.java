package com.ticket.core.mq.producer;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.SearchSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SearchSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    public SearchSyncProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(SearchSyncEvent event) {
        log.info("[MQ-SEARCH] 发送同步事件 type={} id={} op={}",
                event.getType(), event.getTargetId(), event.getOp());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SEARCH_SYNC_EXCHANGE,
                RabbitMQConfig.SEARCH_SYNC_ROUTING_KEY,
                event);
    }
}
