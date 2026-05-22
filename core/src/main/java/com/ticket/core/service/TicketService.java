package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.common.util.TicketNoGenerator;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.TicketMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 票券服务 — 负责为订单项生成对应的票券
 */
@Service
public class TicketService {

    private static final String LOCK_KEY_PREFIX = "ticket:generate:lock:";
    /** 锁等待时间：MQ 重投时另一消费者已持锁,短等待让重投消息直接走幂等返回 */
    private static final long LOCK_WAIT_SECONDS = 5;
    /** 锁持有时间：足够覆盖订单项查询+票号生成+批量插入 */
    private static final long LOCK_LEASE_SECONDS = 30;

    private final OrderItemMapper orderItemMapper;
    private final TicketMapper ticketMapper;
    private final SnowflakeIdGenerator snowflake;
    private final RedissonClient redissonClient;

    public TicketService(OrderItemMapper orderItemMapper,
                         TicketMapper ticketMapper,
                         SnowflakeIdGenerator snowflake,
                         RedissonClient redissonClient) {
        this.orderItemMapper = orderItemMapper;
        this.ticketMapper = ticketMapper;
        this.snowflake = snowflake;
        this.redissonClient = redissonClient;
    }

    /**
     * 为订单生成票券
     *
     * @param orderId 订单 ID
     * @param userId 用户 ID
     * @return 生成的票券列表
     */
    public List<Ticket> generateTicketsForOrder(Long orderId, Long userId) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + orderId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                // 拿不到锁说明另一个消费者正在处理,等其完成后重新读 DB 走幂等返回
                List<Ticket> existing = ticketMapper.selectByOrderId(orderId);
                if (!existing.isEmpty()) {
                    return existing;
                }
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "票券生成获取锁超时,orderId=" + orderId);
            }
            return doGenerateTickets(orderId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "票券生成被中断,orderId=" + orderId);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private List<Ticket> doGenerateTickets(Long orderId, Long userId) {
        // 持锁后再次幂等检查：覆盖锁前已有其他线程完成生成的情况
        List<Ticket> existing = ticketMapper.selectByOrderId(orderId);
        if (!existing.isEmpty()) {
            return existing;
        }

        // 1. 查询订单项
        List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);

        // 2. 为每个订单项生成对应的票券
        List<Ticket> tickets = new ArrayList<>();
        for (OrderItem item : orderItems) {
            Ticket ticket = new Ticket();
            ticket.setId(snowflake.nextId());
            ticket.setSeatId(item.getSeatId());
            ticket.setQrCode(UUID.randomUUID().toString());

            // 生成唯一的票号
            String ticketNo;
            int retryCount = 0;
            final int MAX_RETRIES = 100;
            do {
                if (retryCount >= MAX_RETRIES) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成票号失败：达到最大重试次数");
                }
                ticketNo = TicketNoGenerator.generate();
                retryCount++;
            } while (ticketMapper.selectByTicketNo(ticketNo) != null);

            ticket.setTicketNo(ticketNo);
            ticket.setOrderId(orderId);
            ticket.setUserId(userId);
            ticket.setStatus(0);

            LocalDateTime now = LocalDateTime.now();
            ticket.setCreateTime(now);
            ticket.setUpdateTime(now);

            tickets.add(ticket);
        }

        // 3. 批量插入票券
        ticketMapper.batchInsert(tickets);

        return tickets;
    }
}
