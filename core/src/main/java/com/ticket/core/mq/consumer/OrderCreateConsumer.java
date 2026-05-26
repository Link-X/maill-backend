package com.ticket.core.mq.consumer;

import com.ticket.common.constant.RedisKeys;
import com.ticket.common.exception.BusinessException;
import com.ticket.core.domain.dto.OrderCreateMessage;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.service.OrderCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 异步建单消费者。
 *
 * submit 接口已经完成 锁座 + 限购扣减,本消费者只负责 INSERT 订单+订单项 + 发超时 MQ。
 *
 * 边界处理:
 *  - 幂等:消费前先 selectByOrderNo,已存在直接返回(MQ 至少一次语义)
 *  - 业务异常(座位被抢、价格异常):service 内部已释放座位+退限购,这里只更新 pending key 为 FAILED,
 *    不抛出避免 MQ 重试
 *  - 系统异常(DB 短暂不可用):抛出让 MQ 重试 3 次,期间也标记 FAILED(供前端及时反馈,
 *    重试若最终成功,前端再次轮询会读到 DB 订单覆盖 FAILED 状态)
 */
@Slf4j
@Component
public class OrderCreateConsumer {

    private static final Duration PENDING_TTL = Duration.ofSeconds(60);

    private final OrderCommandService orderCommandService;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    public OrderCreateConsumer(OrderCommandService orderCommandService,
                               OrderMapper orderMapper,
                               StringRedisTemplate redisTemplate) {
        this.orderCommandService = orderCommandService;
        this.orderMapper = orderMapper;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_CREATE_QUEUE)
    public void handle(OrderCreateMessage msg) {
        String orderNo = msg.getOrderNo();
        log.info("[ORDER-CREATE] 收到异步建单消息 orderNo={}", orderNo);

        // 幂等:已存在直接返回,清掉 pending(前端会通过 DB 查到订单)
        if (orderMapper.selectByOrderNo(orderNo) != null) {
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            return;
        }

        OrderCreateRequest req = new OrderCreateRequest();
        req.setOrderNo(orderNo);
        req.setUserId(msg.getUserId());
        req.setSessionId(msg.getSessionId());
        req.setSeatIds(msg.getSeatIds());

        try {
            orderCommandService.createOrder(req);
            // 成功:删除 pending key,前端下一次轮询查 DB 即可拿到完整订单
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            log.info("[ORDER-CREATE] 建单成功 orderNo={}", orderNo);
        } catch (BusinessException e) {
            // 业务失败:service 内部已经释放座位 + 退限购,这里只更新前端可见的状态
            String reason = e.getMessage() != null ? e.getMessage() : "下单失败";
            redisTemplate.opsForValue().set(
                    RedisKeys.orderCreatePending(orderNo), "FAILED:" + reason, PENDING_TTL);
            log.warn("[ORDER-CREATE] 业务失败 orderNo={} reason={}", orderNo, reason);
            // 不抛:业务失败重试也不会成功
        } catch (Exception e) {
            redisTemplate.opsForValue().set(
                    RedisKeys.orderCreatePending(orderNo), "FAILED:系统繁忙,请稍后重试", PENDING_TTL);
            log.error("[ORDER-CREATE] 系统异常 orderNo={},将由 MQ 重试", orderNo, e);
            throw e; // 抛出让 MQ retry
        }
    }
}
