package com.ticket.core.mq;

import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.SearchSyncEvent;
import com.ticket.core.mq.producer.SearchSyncProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchSyncProducerTest {

    @Test
    void should_send_upsert_event_with_correct_routing() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        SearchSyncProducer producer = new SearchSyncProducer(template);

        SearchSyncEvent event = SearchSyncEvent.upsert("show", 42L);
        producer.send(event);

        ArgumentCaptor<String> exchange = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        verify(template).convertAndSend(exchange.capture(), routingKey.capture(), payload.capture());

        assertEquals(RabbitMQConfig.SEARCH_SYNC_EXCHANGE, exchange.getValue());
        assertEquals(RabbitMQConfig.SEARCH_SYNC_ROUTING_KEY, routingKey.getValue());
        assertEquals(event, payload.getValue());
    }
}
