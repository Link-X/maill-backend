package com.ticket.core.mq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // 订单超时相关
    public static final String ORDER_TIMEOUT_EXCHANGE    = "order.timeout.exchange";
    public static final String ORDER_TIMEOUT_QUEUE       = "order.timeout.queue";
    public static final String ORDER_DEAD_EXCHANGE       = "order.dead.exchange";
    public static final String ORDER_CANCEL_QUEUE        = "order.cancel.queue";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";
    public static final String ORDER_CANCEL_ROUTING_KEY  = "order.cancel";

    // 订单异步建单相关:submit 锁座后立即返回临时单号,实际 INSERT 走异步队列
    public static final String ORDER_CREATE_EXCHANGE     = "order.create.exchange";
    public static final String ORDER_CREATE_QUEUE        = "order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY  = "order.create";

    // 退款相关
    public static final String REFUND_EXCHANGE    = "refund.exchange";
    public static final String REFUND_QUEUE       = "refund.queue";
    public static final String REFUND_ROUTING_KEY = "refund";

    // 支付成功相关
    public static final String PAYMENT_SUCCESS_EXCHANGE  = "payment.success.exchange";
    public static final String TICKET_GENERATE_QUEUE     = "ticket.generate.queue";
    public static final String INVENTORY_SYNC_QUEUE      = "inventory.sync.queue";
    public static final String NOTIFICATION_QUEUE        = "notification.queue";

    // 搜索同步相关
    public static final String SEARCH_SYNC_EXCHANGE    = "search.sync.exchange";
    public static final String SEARCH_SYNC_QUEUE       = "search.sync.queue";
    public static final String SEARCH_SYNC_ROUTING_KEY = "search.sync";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate：开启 mandatory + Publisher Confirm + Returns 回调。
     *
     * 依赖 yml 配置：
     *   spring.rabbitmq.publisher-confirm-type: correlated
     *   spring.rabbitmq.publisher-returns: true
     *
     * 任一消息未到达 Exchange(nack) 或路由不到 Queue(returned) 都会写 ERROR 日志，
     * 运维侧应基于该日志接告警。生产端要求资金/票券链路必须不能静默丢失。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData != null ? correlationData.getId() : "null";
                log.error("[MQ-CONFIRM] 消息未到达 Exchange, id={}, cause={}", id, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "[MQ-RETURN] 路由失败 exchange={} routingKey={} replyCode={} replyText={} body={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getMessage()));
        return template;
    }

    /** 订单超时投递交换机 */
    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDER_TIMEOUT_EXCHANGE, true, false);
    }

    /** 死信交换机：接收超时的订单消息 */
    @Bean
    public DirectExchange orderDeadExchange() {
        return new DirectExchange(ORDER_DEAD_EXCHANGE, true, false);
    }

    /** 订单超时队列：5 分钟 TTL，到期后转发死信交换机 */
    @Bean
    public Queue orderTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 300_000);                   // 5 分钟
        args.put("x-dead-letter-exchange", ORDER_DEAD_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING_KEY);
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).withArguments(args).build();
    }

    /** 订单取消队列：消费死信，执行实际取消逻辑 */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(ORDER_CANCEL_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderTimeoutExchange())
                .with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderDeadExchange())
                .with(ORDER_CANCEL_ROUTING_KEY);
    }

    /** 支付成功 Fanout 交换机 */
    @Bean
    public FanoutExchange paymentSuccessExchange() {
        return new FanoutExchange(PAYMENT_SUCCESS_EXCHANGE, true, false);
    }

    @Bean
    public Queue ticketGenerateQueue() {
        return QueueBuilder.durable(TICKET_GENERATE_QUEUE).build();
    }

    @Bean
    public Queue inventorySyncQueue() {
        return QueueBuilder.durable(INVENTORY_SYNC_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding ticketGenerateBinding() {
        return BindingBuilder.bind(ticketGenerateQueue()).to(paymentSuccessExchange());
    }

    @Bean
    public Binding inventorySyncBinding() {
        return BindingBuilder.bind(inventorySyncQueue()).to(paymentSuccessExchange());
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(paymentSuccessExchange());
    }

    @Bean
    public DirectExchange refundExchange() {
        return new DirectExchange(REFUND_EXCHANGE, true, false);
    }

    @Bean
    public Queue refundQueue() {
        return QueueBuilder.durable(REFUND_QUEUE).build();
    }

    @Bean
    public Binding refundBinding() {
        return BindingBuilder.bind(refundQueue()).to(refundExchange()).with(REFUND_ROUTING_KEY);
    }

    /** 异步建单交换机 */
    @Bean
    public DirectExchange orderCreateExchange() {
        return new DirectExchange(ORDER_CREATE_EXCHANGE, true, false);
    }

    /** 异步建单队列 */
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE).build();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue()).to(orderCreateExchange()).with(ORDER_CREATE_ROUTING_KEY);
    }

    /** 搜索同步交换机 */
    @Bean
    public DirectExchange searchSyncExchange() {
        return new DirectExchange(SEARCH_SYNC_EXCHANGE, true, false);
    }

    /** 搜索同步队列 */
    @Bean
    public Queue searchSyncQueue() {
        return QueueBuilder.durable(SEARCH_SYNC_QUEUE).build();
    }

    /** 绑定:搜索同步队列 ↔ 搜索同步交换机 */
    @Bean
    public Binding searchSyncBinding() {
        return BindingBuilder
                .bind(searchSyncQueue())
                .to(searchSyncExchange())
                .with(SEARCH_SYNC_ROUTING_KEY);
    }
}
