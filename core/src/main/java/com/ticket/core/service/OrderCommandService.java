package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.producer.OrderTimeoutProducer;
import com.ticket.core.mq.producer.RefundProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单命令服务：下单 / 取消 / 退款 等写入路径。
 *
 * 与 {@link OrderQueryService} 配对。
 * 这里保持事务边界清晰，只引入写入所需的 Mapper / Service / Producer，不混入查询装配。
 */
@Slf4j
@Service
public class OrderCommandService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatMapper seatMapper;
    private final ShowSessionMapper showSessionMapper;
    private final OrderTimeoutProducer orderTimeoutProducer;
    private final RefundProducer refundProducer;
    private final TicketMapper ticketMapper;
    private final SnowflakeIdGenerator snowflake;

    public OrderCommandService(OrderMapper orderMapper,
                               OrderItemMapper orderItemMapper,
                               SeatInventoryService inventoryService,
                               PurchaseLimitService purchaseLimitService,
                               SeatMapper seatMapper,
                               ShowSessionMapper showSessionMapper,
                               OrderTimeoutProducer orderTimeoutProducer,
                               RefundProducer refundProducer,
                               TicketMapper ticketMapper,
                               SnowflakeIdGenerator snowflake) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
        this.seatMapper = seatMapper;
        this.showSessionMapper = showSessionMapper;
        this.orderTimeoutProducer = orderTimeoutProducer;
        this.refundProducer = refundProducer;
        this.ticketMapper = ticketMapper;
        this.snowflake = snowflake;
    }

    /**
     * 创建订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderCreateRequest request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();
        List<Long> seatIds = request.getSeatIds();

        // 1. 超卖兜底
        List<Long> occupied = orderItemMapper.selectOccupiedSeatIds(seatIds);
        if (!occupied.isEmpty()) {
            for (Long id : seatIds) {
                inventoryService.releaseSeat(sessionId, id);
            }
            purchaseLimitService.decrement(sessionId, userId, seatIds.size());
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        // 2. 从 DB 加载座位信息，校验情侣连座完整性
        List<Seat> seatList = seatMapper.selectByIds(seatIds);
        if (seatList.size() != seatIds.size()) {
            for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
            purchaseLimitService.decrement(sessionId, userId, seatIds.size());
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        Set<Long> seatIdSet = new HashSet<>(seatIds);
        for (Seat seat : seatList) {
            if (seat.getType() == 2 || seat.getType() == 3) {
                if (seat.getPairSeatId() == null || !seatIdSet.contains(seat.getPairSeatId())) {
                    for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
                    purchaseLimitService.decrement(sessionId, userId, seatIds.size());
                    throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                }
            }
        }

        // 3. 计算总价
        Map<Long, Seat> seatMap = seatList.stream().collect(Collectors.toMap(Seat::getId, s -> s));

        List<OrderItem> items = new ArrayList<>();
        for (Long seatId : seatIds) {
            Seat seat = seatMap.get(seatId);
            String priceStr = inventoryService.getAreaPrice(sessionId, seat.getAreaId());
            if (priceStr == null) {
                for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
                purchaseLimitService.decrement(sessionId, userId, seatIds.size());
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            BigDecimal price = new BigDecimal(priceStr);
            OrderItem item = new OrderItem();
            item.setOrderId(0L);
            item.setSeatId(seatId);
            item.setPrice(price);
            item.setSeatInfo(seat.getSeatName() != null ? seat.getSeatName()
                    : "row:" + seat.getRowNo() + ",col:" + seat.getColNo());
            items.add(item);
        }

        // 4. 计算总金额并创建订单
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setOrderNo(String.valueOf(snowflake.nextId()));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(5));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemMapper.batchInsert(items);

        // 事务提交后再消费座位 + 发超时 MQ
        Long orderId = order.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long seatId : seatIds) {
                    inventoryService.consumeSeat(sessionId, seatId, String.valueOf(userId));
                }
                orderTimeoutProducer.sendTimeoutMessage(orderId);
            }
        });

        return order;
    }

    /** 用户主动取消（写 cancel_reason=0） */
    public void cancelByUser(Long orderId) {
        cancelOrder(orderId, 0);
    }

    /** 超时自动取消（写 cancel_reason=1），由 OrderTimeoutConsumer 调 */
    public void cancelByTimeout(Long orderId) {
        cancelOrder(orderId, 1);
    }

    /**
     * 取消订单（仅未支付）。
     * @param cancelReason 0=用户主动 1=超时自动
     */
    @Transactional(rollbackFor = Exception.class)
    protected void cancelOrder(Long orderId, int cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) {
            return;
        }

        int affected = orderMapper.cancelWithReason(orderId, cancelReason);
        if (affected == 0) {
            return;
        }

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        Long sessionId = order.getSessionId();
        Long userId = order.getUserId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (OrderItem item : items) {
                    inventoryService.releaseSeat(sessionId, item.getSeatId());
                }
                purchaseLimitService.decrement(sessionId, userId, items.size());
            }
        });
    }

    /**
     * 发起退款（已支付订单取消）
     */
    public void initiateRefund(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || (order.getStatus() != 1 && order.getStatus() != 5)) {
            return;
        }

        checkRefundTimeLimit(order);

        List<Ticket> tickets = ticketMapper.selectByOrderId(orderId);
        List<Long> refundableSeatIds = tickets.stream()
                .filter(t -> t.getStatus() == 0)
                .map(Ticket::getSeatId)
                .collect(Collectors.toList());

        if (refundableSeatIds.isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_ALL_TICKETS_USED);
        }

        doRefund(order, refundableSeatIds);
    }

    /**
     * 按票号退单张票
     */
    public void initiateTicketRefund(String orderNo, String ticketNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() != 1 && order.getStatus() != 5) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单状态不允许退款");
        }

        checkRefundTimeLimit(order);

        Ticket ticket = ticketMapper.selectByTicketNo(ticketNo);
        if (ticket == null || !ticket.getOrderId().equals(order.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "票券不存在或不属于该订单");
        }
        if (ticket.getStatus() == 1) {
            throw new BusinessException(ErrorCode.TICKET_ALREADY_USED);
        }
        if (ticket.getStatus() == 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "票券已退款");
        }

        doRefund(order, List.of(ticket.getSeatId()));
    }

    private void checkRefundTimeLimit(Order order) {
        ShowSession session = showSessionMapper.selectById(order.getSessionId());
        if (session != null && session.getStartTime() != null) {
            long hoursToStart = ChronoUnit.HOURS.between(LocalDateTime.now(), session.getStartTime());
            if (hoursToStart < 24) {
                throw new BusinessException(ErrorCode.REFUND_TOO_CLOSE_TO_START);
            }
        }
    }

    private void doRefund(Order order, List<Long> refundSeatIds) {
        int affected = orderMapper.updateStatusFrom(order.getId(), order.getStatus(), 3);
        if (affected == 0) {
            return;
        }

        // 计算本次退款金额（按 order_item.price 累加），并累加到 order.refund_amount。
        // 部分退款会触发多次 doRefund，每次只算本次涉及的座位金额。
        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        java.util.Set<Long> refundSet = new java.util.HashSet<>(refundSeatIds);
        BigDecimal refundIncr = items.stream()
                .filter(it -> refundSet.contains(it.getSeatId()))
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundIncr.compareTo(BigDecimal.ZERO) > 0) {
            orderMapper.addRefundAmount(order.getId(), refundIncr);
        }

        try {
            refundProducer.sendRefund(order.getId(), refundSeatIds);
        } catch (Exception e) {
            log.error("退款 MQ 发送失败，回滚订单状态，orderId={}", order.getId(), e);
            orderMapper.updateStatusFrom(order.getId(), 3, order.getStatus());
            // 回滚 refund_amount（减回去）
            if (refundIncr.compareTo(BigDecimal.ZERO) > 0) {
                orderMapper.addRefundAmount(order.getId(), refundIncr.negate());
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "退款系统繁忙，请稍后重试");
        }

        try {
            for (Long seatId : refundSeatIds) {
                inventoryService.releaseSeat(order.getSessionId(), seatId);
            }
        } catch (Exception e) {
            log.error("退款座位释放失败,等待消费者兜底,orderId={},seatIds={}",
                    order.getId(), refundSeatIds, e);
        }
    }
}
