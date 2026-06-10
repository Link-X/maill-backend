package com.ticket.core.mq.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // 派座模式异步建单相关:submit/by-area 扣库存后立即返回单号,实际派座+INSERT 走异步队列
    public static final String ORDER_ALLOCATE_EXCHANGE     = "order.allocate.exchange";
    public static final String ORDER_ALLOCATE_QUEUE        = "order.allocate.queue";
    public static final String ORDER_ALLOCATE_ROUTING_KEY  = "order.allocate";

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

    // 死信相关:所有业务队列重试耗尽后消息进入对应 DLQ,而非静默丢弃。
    // 资金/票券链路(退款、出票、库存同步)的消息丢失会直接造成资损,必须可追溯、可人工补偿。
    public static final String DLX_EXCHANGE       = "business.dlx.exchange";
    public static final String DLQ_SUFFIX         = ".dlq";
    public static final String DLQ_ROUTING_PREFIX = "dlq.";

    // DLQ 名称常量(编译期常量,供 @RabbitListener 引用)
    public static final String ORDER_CANCEL_DLQ    = ORDER_CANCEL_QUEUE + DLQ_SUFFIX;
    public static final String ORDER_CREATE_DLQ    = ORDER_CREATE_QUEUE + DLQ_SUFFIX;
    public static final String ORDER_ALLOCATE_DLQ  = ORDER_ALLOCATE_QUEUE + DLQ_SUFFIX;
    public static final String REFUND_DLQ          = REFUND_QUEUE + DLQ_SUFFIX;
    public static final String TICKET_GENERATE_DLQ = TICKET_GENERATE_QUEUE + DLQ_SUFFIX;
    public static final String INVENTORY_SYNC_DLQ  = INVENTORY_SYNC_QUEUE + DLQ_SUFFIX;
    public static final String NOTIFICATION_DLQ    = NOTIFICATION_QUEUE + DLQ_SUFFIX;
    public static final String SEARCH_SYNC_DLQ     = SEARCH_SYNC_QUEUE + DLQ_SUFFIX;

    /** 需要挂 DLX 的业务队列清单(order.timeout.queue 除外,其 TTL 死信是业务路由机制) */
    private static final String[] DLX_QUEUES = {
            ORDER_CANCEL_QUEUE, ORDER_CREATE_QUEUE, ORDER_ALLOCATE_QUEUE,
            REFUND_QUEUE, TICKET_GENERATE_QUEUE, INVENTORY_SYNC_QUEUE,
            NOTIFICATION_QUEUE, SEARCH_SYNC_QUEUE
    };

    /** 业务队列统一构建:durable + 死信参数。重试耗尽 reject 后路由到 DLX_EXCHANGE → {队列名}.dlq */
    private static Queue durableWithDlx(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_PREFIX + queueName)
                .build();
    }

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
                                         MessageConverter messageConverter,
                                         MeterRegistry meterRegistry) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = correlationData != null ? correlationData.getId() : "null";
                log.error("[MQ-CONFIRM] 消息未到达 Exchange, id={}, cause={}", id, cause);
                meterRegistry.counter("mq.publish.confirm.fail").increment();
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("[MQ-RETURN] 路由失败 exchange={} routingKey={} replyCode={} replyText={} body={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    returned.getMessage());
            meterRegistry.counter("mq.publish.return",
                    "exchange", returned.getExchange()).increment();
        });
        return template;
    }

    /**
     * 死信基础设施:统一 DLX + 每个业务队列一个专属 DLQ。
     * DLQ 由 DeadLetterConsumer 消费:写 ERROR 日志(含完整消息体,供人工补偿重发)+ 指标告警。
     */
    @Bean
    public Declarables deadLetterDeclarables() {
        DirectExchange dlx = new DirectExchange(DLX_EXCHANGE, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(dlx);
        for (String queueName : DLX_QUEUES) {
            Queue dlq = QueueBuilder.durable(queueName + DLQ_SUFFIX).build();
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(DLQ_ROUTING_PREFIX + queueName));
        }
        return new Declarables(declarables);
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

    /** 订单取消队列：消费死信，执行实际取消逻辑；重试耗尽后进自己的 DLQ */
    @Bean
    public Queue orderCancelQueue() {
        return durableWithDlx(ORDER_CANCEL_QUEUE);
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
        return durableWithDlx(TICKET_GENERATE_QUEUE);
    }

    @Bean
    public Queue inventorySyncQueue() {
        return durableWithDlx(INVENTORY_SYNC_QUEUE);
    }

    @Bean
    public Queue notificationQueue() {
        return durableWithDlx(NOTIFICATION_QUEUE);
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
        return durableWithDlx(REFUND_QUEUE);
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
        return durableWithDlx(ORDER_CREATE_QUEUE);
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue()).to(orderCreateExchange()).with(ORDER_CREATE_ROUTING_KEY);
    }

    /** 派座异步建单交换机 */
    @Bean
    public DirectExchange orderAllocateExchange() {
        return new DirectExchange(ORDER_ALLOCATE_EXCHANGE, true, false);
    }

    /** 派座异步建单队列 */
    @Bean
    public Queue orderAllocateQueue() {
        return durableWithDlx(ORDER_ALLOCATE_QUEUE);
    }

    @Bean
    public Binding orderAllocateBinding() {
        return BindingBuilder.bind(orderAllocateQueue())
                .to(orderAllocateExchange())
                .with(ORDER_ALLOCATE_ROUTING_KEY);
    }

    /** 搜索同步交换机 */
    @Bean
    public DirectExchange searchSyncExchange() {
        return new DirectExchange(SEARCH_SYNC_EXCHANGE, true, false);
    }

    /** 搜索同步队列 */
    @Bean
    public Queue searchSyncQueue() {
        return durableWithDlx(SEARCH_SYNC_QUEUE);
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
