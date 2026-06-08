package com.ticket.core.mq.consumer;

import com.ticket.common.constant.RedisKeys;
import com.ticket.common.exception.BusinessException;
import com.ticket.core.domain.dto.OrderAllocateMessage;
import com.ticket.core.domain.entity.SeatAllocationTask;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.SeatAllocationTaskMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.service.OrderCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 派座模式异步建单消费者。
 *
 * <p>submit/by-area 已经完成 限购扣减 + Redis 库存扣减 + 写 task=PENDING,本消费者负责:
 *  1. 从 area:pool 取座(allocationService.allocate)
 *  2. INSERT 订单+订单项
 *  3. task 状态 PENDING → SUCCESS / ROLLED_BACK
 *
 * <p>边界:
 *  - 幂等:消费前先 selectByOrderNo,已存在直接返回(MQ 至少一次语义)
 *  - 业务异常(库存/池子):service 内部已回滚库存+池子+退限购,这里只更新 pending key 为 FAILED
 *  - 系统异常:抛出让 MQ 重试,期间也标记 FAILED 让前端及时反馈
 */
@Slf4j
@Component
public class OrderAllocateConsumer {

    private static final Duration PENDING_TTL = Duration.ofSeconds(60);

    private final OrderCommandService orderCommandService;
    private final OrderMapper orderMapper;
    private final SeatAllocationTaskMapper allocationTaskMapper;
    private final StringRedisTemplate redisTemplate;

    public OrderAllocateConsumer(OrderCommandService orderCommandService,
                                 OrderMapper orderMapper,
                                 SeatAllocationTaskMapper allocationTaskMapper,
                                 StringRedisTemplate redisTemplate) {
        this.orderCommandService = orderCommandService;
        this.orderMapper = orderMapper;
        this.allocationTaskMapper = allocationTaskMapper;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_ALLOCATE_QUEUE)
    public void handle(OrderAllocateMessage msg) {
        String orderNo = msg.getOrderNo();
        log.info("[ORDER-ALLOCATE] 收到异步派座建单消息 orderNo={}", orderNo);

        // 幂等:DB 已有订单直接返回。
        // 关键:必须同时把 task 从 PENDING 升级为 SUCCESS,否则在"INSERT 成功后崩溃,
        // updateStatusFrom 没执行"的窗口期,SeatAllocationRecoveryScheduler 会误把
        // 这单的座位还回池子(已售的座位被错误释放)。
        if (orderMapper.selectByOrderNo(orderNo) != null) {
            allocationTaskMapper.updateStatusFrom(orderNo,
                    SeatAllocationTask.STATUS_PENDING,
                    SeatAllocationTask.STATUS_SUCCESS,
                    null, null);
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            return;
        }

        try {
            orderCommandService.createOrderByAllocation(orderNo);
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            log.info("[ORDER-ALLOCATE] 派座建单成功 orderNo={}", orderNo);
        } catch (BusinessException e) {
            String reason = e.getMessage() != null ? e.getMessage() : "派座失败";
            redisTemplate.opsForValue().set(
                    RedisKeys.orderCreatePending(orderNo), "FAILED:" + reason, PENDING_TTL);
            log.warn("[ORDER-ALLOCATE] 业务失败 orderNo={} reason={}", orderNo, reason);
            // 业务失败不重试
        } catch (Exception e) {
            redisTemplate.opsForValue().set(
                    RedisKeys.orderCreatePending(orderNo), "FAILED:系统繁忙,请稍后重试", PENDING_TTL);
            log.error("[ORDER-ALLOCATE] 系统异常 orderNo={},将由 MQ 重试", orderNo, e);
            throw e;
        }
    }
}
