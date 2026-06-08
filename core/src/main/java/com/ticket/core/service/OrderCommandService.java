package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatAllocationTask;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.entity.Ticket;
import com.ticket.core.mapper.OrderItemMapper;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.SeatAllocationTaskMapper;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import com.ticket.core.mapper.TicketMapper;
import com.ticket.core.mq.producer.OrderTimeoutProducer;
import com.ticket.core.mq.producer.RefundProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单命令服务 — 下单 / 取消 / 退款 写入路径。
 *
 * <p>下单按售卖模式分两个入口,共用同一个内部建单实现:
 * <ul>
 *   <li>{@link #createOrderBySeats}      选座模式:Controller 已锁座,本方法做超卖兜底 + 情侣完整性 + 建单</li>
 *   <li>{@link #createOrderByAllocation} 派座模式:Controller 已扣库存,本方法派座 + 建单</li>
 * </ul>
 *
 * <p>关键事务边界:外层入口方法**不**带 @Transactional,这样异常补偿(回滚 Redis、更新 task 状态)
 * 不会被事务回滚影响;真正需要原子的 INSERT 通过自注入 {@code self.createOrderInternal} 进入事务方法。
 *
 * <p>取消/退款时,根据每个 seat 所属区域的 sale_mode 自动分流释放(选座回 session SET + 删 lock,
 * 派座额外还回 area:pool + 库存计数器)。情侣座成对退还,绝不拆散。
 */
@Slf4j
@Service
public class OrderCommandService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeatInventoryService inventoryService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatMapper seatMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final ShowSessionMapper showSessionMapper;
    private final OrderTimeoutProducer orderTimeoutProducer;
    private final RefundProducer refundProducer;
    private final TicketMapper ticketMapper;
    private final SnowflakeIdGenerator snowflake;
    private final SeatAllocationService allocationService;
    private final SeatAllocationTaskMapper allocationTaskMapper;

    /** 自注入用于走代理调用 createOrderInternal,使其 @Transactional 生效 */
    @Autowired @Lazy
    private OrderCommandService self;

    public OrderCommandService(OrderMapper orderMapper,
                               OrderItemMapper orderItemMapper,
                               SeatInventoryService inventoryService,
                               PurchaseLimitService purchaseLimitService,
                               SeatMapper seatMapper,
                               SeatAreaMapper seatAreaMapper,
                               ShowSessionMapper showSessionMapper,
                               OrderTimeoutProducer orderTimeoutProducer,
                               RefundProducer refundProducer,
                               TicketMapper ticketMapper,
                               SnowflakeIdGenerator snowflake,
                               SeatAllocationService allocationService,
                               SeatAllocationTaskMapper allocationTaskMapper) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.inventoryService = inventoryService;
        this.purchaseLimitService = purchaseLimitService;
        this.seatMapper = seatMapper;
        this.seatAreaMapper = seatAreaMapper;
        this.showSessionMapper = showSessionMapper;
        this.orderTimeoutProducer = orderTimeoutProducer;
        this.refundProducer = refundProducer;
        this.ticketMapper = ticketMapper;
        this.snowflake = snowflake;
        this.allocationService = allocationService;
        this.allocationTaskMapper = allocationTaskMapper;
    }

    // ===================== 下单入口 A:选座模式 =====================

    /**
     * 选座模式建单:Controller 已完成 限购扣减 + Redis Lua 锁座,
     * 本方法做超卖兜底 + 情侣完整性校验 + 建单。
     *
     * 不带 @Transactional:1/2 步只读 + Redis 补偿,3 步通过 self 走事务方法。
     */
    public Order createOrderBySeats(OrderCreateRequest request) {
        Long sessionId = request.getSessionId();
        Long userId = request.getUserId();
        List<Long> seatIds = request.getSeatIds();

        // 1. 超卖兜底
        List<Long> occupied = orderItemMapper.selectOccupiedSeatIds(seatIds);
        if (!occupied.isEmpty()) {
            rollbackBySeats(sessionId, userId, seatIds);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        // 2. 从 DB 加载座位信息,校验情侣连座完整性
        List<Seat> seatList = seatMapper.selectByIds(seatIds);
        if (seatList.size() != seatIds.size()) {
            rollbackBySeats(sessionId, userId, seatIds);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        Set<Long> seatIdSet = new HashSet<>(seatIds);
        for (Seat seat : seatList) {
            if (seat.getType() != null && (seat.getType() == 2 || seat.getType() == 3)) {
                if (seat.getPairSeatId() == null || !seatIdSet.contains(seat.getPairSeatId())) {
                    rollbackBySeats(sessionId, userId, seatIds);
                    throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
                }
            }
        }

        // 3. 进入纯建单(走 self 代理,保证 @Transactional 生效)
        try {
            return self.createOrderInternal(request.getOrderNo(), userId, sessionId, seatList,
                    /* afterCommitConsume */ () -> {
                        for (Long seatId : seatIds) {
                            inventoryService.consumeSeat(sessionId, seatId, String.valueOf(userId));
                        }
                    },
                    /* inTxnAfterInsert */ null);
        } catch (RuntimeException e) {
            // 事务已回滚,这里补偿 Redis 与限购
            rollbackBySeats(sessionId, userId, seatIds);
            throw e;
        }
    }

    /** 兼容旧调用名 */
    public Order createOrder(OrderCreateRequest request) {
        return createOrderBySeats(request);
    }

    private void rollbackBySeats(Long sessionId, Long userId, List<Long> seatIds) {
        for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
        purchaseLimitService.decrement(sessionId, userId, seatIds.size());
    }

    // ===================== 下单入口 B:派座模式 =====================

    /**
     * 派座模式建单:Controller 已完成 限购扣减 + Redis 库存扣减 + 写 task=PENDING + 发 MQ,
     * 本方法在异步消费时执行:从池里取座 → 建单 → 把 task 置为 SUCCESS。
     *
     * 不带 @Transactional:这样异常补偿(rollback Redis、updateStatusFrom)不会被事务回滚。
     * 真正的 INSERT 通过 self.createOrderInternal 进入事务方法。
     */
    public Order createOrderByAllocation(String orderNo) {
        SeatAllocationTask task = allocationTaskMapper.selectByOrderNo(orderNo);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "派座任务不存在");
        }
        // 幂等分支:
        //  SUCCESS    → 订单已建好,直接返回(consumer 据此 deletePending,前端轮询 DB 拿订单)
        //  ROLLED_BACK/FAILED → 抛 BusinessException,consumer 据此 setPending=FAILED 反馈前端
        if (task.getStatus() == SeatAllocationTask.STATUS_SUCCESS) {
            return orderMapper.selectByOrderNo(orderNo);
        }
        if (task.getStatus() == SeatAllocationTask.STATUS_ROLLED_BACK
                || task.getStatus() == SeatAllocationTask.STATUS_FAILED) {
            String reason = task.getFailReason() != null ? task.getFailReason() : "派座任务已失败";
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, reason);
        }
        // 否则 PENDING,继续派座

        long sessionId = task.getSessionId();
        long userId = task.getUserId();
        String areaId = task.getAreaId();
        int ticketType = task.getTicketType();
        int quantity = task.getQuantity();
        // 实际座位张数:情侣 = 对数*2
        int seatCount = ticketType == SeatAllocationTask.TICKET_TYPE_COUPLE ? quantity * 2 : quantity;

        // 1. 从池里取座 — 幂等防御:如果重试时 allocated_seats 已有值,说明前一次 popFromPool
        //    已经执行过,直接用已派出的 seatIds 续做建单,避免再次 popFromPool 导致池/库存计数器漂移。
        List<Long> seatIds;
        java.util.Map<String, Double> memberToScoreForRollback;
        boolean alreadyAllocated = task.getAllocatedSeats() != null && !task.getAllocatedSeats().isEmpty();

        if (alreadyAllocated) {
            log.info("[ALLOCATE-CREATE] 重试场景:沿用已派座位 orderNo={} seats={}",
                    orderNo, task.getAllocatedSeats());
            seatIds = parseAllocatedSeats(task.getAllocatedSeats());
            // 重建 memberToScore(为可能的建单失败回滚)
            memberToScoreForRollback = rebuildMemberToScore(seatIds, ticketType);
        } else {
            SeatAllocationService.AllocationResult allocation;
            try {
                allocation = allocationService.allocate(
                        new SeatAllocationService.StockReservation(sessionId, areaId, ticketType, quantity));
            } catch (RuntimeException e) {
                // 池子取座失败 — 回滚库存 + 退限购 + task 置为 ROLLED_BACK
                allocationService.rollbackStockOnly(sessionId, areaId, ticketType, quantity);
                purchaseLimitService.decrement(sessionId, userId, seatCount);
                allocationTaskMapper.updateStatusFrom(orderNo,
                        SeatAllocationTask.STATUS_PENDING,
                        SeatAllocationTask.STATUS_ROLLED_BACK,
                        null, "派座失败:" + e.getMessage());
                throw e;
            }
            seatIds = allocation.seatIds;
            memberToScoreForRollback = allocation.memberToScore;

            // 立刻持久化 allocated_seats(status 仍 PENDING) — 给 scheduler/重试的判断依据
            String allocatedStr = seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            try {
                allocationTaskMapper.updateAllocatedOnly(orderNo, allocatedStr);
            } catch (Exception persistErr) {
                log.error("[ALLOCATE-CREATE] 写 allocated_seats 失败 orderNo={}", orderNo, persistErr);
                allocationService.rollbackAllocation(sessionId, areaId, ticketType, memberToScoreForRollback);
                purchaseLimitService.decrement(sessionId, userId, seatCount);
                allocationTaskMapper.updateStatusFrom(orderNo,
                        SeatAllocationTask.STATUS_PENDING,
                        SeatAllocationTask.STATUS_ROLLED_BACK,
                        null, "派座任务持久化失败");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "派座任务持久化失败");
            }
        }

        String allocatedStr = seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 2. 加载座位并建单
        try {
            List<Seat> seatList = seatMapper.selectByIds(seatIds);
            if (seatList.size() != seatIds.size()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "派座座位数据缺失");
            }

            final List<Long> seatIdsForConsume = seatIds;
            final String allocatedStrFinal = allocatedStr;
            Order order = self.createOrderInternal(orderNo, userId, sessionId, seatList,
                    /* afterCommitConsume */ () -> {
                        // 派座座位:从全场可售集合 SREM(座位本就没在 seat:lock 里,无需删 lock)
                        for (Long seatId : seatIdsForConsume) {
                            inventoryService.consumeAllocatedSeat(sessionId, seatId);
                        }
                    },
                    /* inTxnAfterInsert */ () -> {
                        // 关键:task=SUCCESS 与 INSERT 必须原子提交。否则若 INSERT 成功后
                        // updateStatusFrom 失败,catch 会错把已售座位还回池子导致超卖。
                        allocationTaskMapper.updateStatusFrom(orderNo,
                                SeatAllocationTask.STATUS_PENDING,
                                SeatAllocationTask.STATUS_SUCCESS,
                                allocatedStrFinal, null);
                    });

            return order;
        } catch (RuntimeException e) {
            // 建单失败(INSERT 或 task=SUCCESS 任一失败)— 释放已派的座位回池 + 退限购 + task → ROLLED_BACK
            allocationService.rollbackAllocation(sessionId, areaId, ticketType, memberToScoreForRollback);
            purchaseLimitService.decrement(sessionId, userId, seatCount);
            allocationTaskMapper.updateStatusFrom(orderNo,
                    SeatAllocationTask.STATUS_PENDING,
                    SeatAllocationTask.STATUS_ROLLED_BACK,
                    null, "建单失败:" + e.getMessage());
            throw e;
        }
    }

    /** 把 task.allocated_seats(CSV) 反解为 seatId 列表 */
    private List<Long> parseAllocatedSeats(String csv) {
        List<Long> result = new ArrayList<>();
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            result.add(Long.parseLong(trimmed));
        }
        return result;
    }

    /**
     * 从 seatIds 反查 row/col 重建 memberToScore(用于重试场景下回滚需要)。
     * 单座:member=seatId, score=row*100000+col
     * 情侣:member="left:right", score=left.row*100000+left.col;调用方传入的 seatIds 必须是成对的
     */
    private java.util.Map<String, Double> rebuildMemberToScore(List<Long> seatIds, int ticketType) {
        java.util.Map<String, Double> result = new java.util.HashMap<>();
        List<Seat> seats = seatMapper.selectByIds(seatIds);
        java.util.Map<Long, Seat> idx = seats.stream().collect(Collectors.toMap(Seat::getId, s -> s));
        if (ticketType == SeatAllocationTask.TICKET_TYPE_SINGLE) {
            for (Long sid : seatIds) {
                Seat s = idx.get(sid);
                if (s == null) continue;
                result.put(String.valueOf(sid),
                        (double) s.getRowNo() * 100000 + s.getColNo());
            }
        } else {
            // 按 pair_seat_id 分组成 (left,right)
            java.util.Set<Long> done = new java.util.HashSet<>();
            for (Long sid : seatIds) {
                if (done.contains(sid)) continue;
                Seat s = idx.get(sid);
                if (s == null || s.getPairSeatId() == null) continue;
                Seat pair = idx.get(s.getPairSeatId());
                if (pair == null) continue;
                done.add(s.getId());
                done.add(pair.getId());
                Seat left  = s.getColNo() <= pair.getColNo() ? s : pair;
                Seat right = left == s ? pair : s;
                String member = left.getId() + ":" + right.getId();
                result.put(member, (double) left.getRowNo() * 100000 + left.getColNo());
            }
        }
        return result;
    }

    // ===================== 纯建单核心(两种模式共用) =====================

    /**
     * 纯建单:座位已被持有(锁/池子已取出),本方法只算价、建 order/order_item、注册 afterCommit。
     *
     * <p>设计为 public + @Transactional,以便通过 self 代理调用让 @Transactional 生效。
     * 调用方应负责异常补偿(释放 Redis、退限购) — 本方法事务回滚只保证 DB 一致。
     *
     * @param afterCommitConsume   事务提交后执行,由调用方按模式定制(消费座位、发超时 MQ 等)
     * @param inTxnAfterInsert     事务内 INSERT 之后执行,允许调用方把"必须与建单原子提交"的 DB
     *                             操作放进来(如派座模式需要把 task PENDING→SUCCESS),
     *                             如果失败会与 INSERT 一起回滚。可传 null。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrderInternal(String preGeneratedOrderNo, Long userId, Long sessionId,
                                     List<Seat> seatList,
                                     Runnable afterCommitConsume,
                                     Runnable inTxnAfterInsert) {
        List<OrderItem> items = new ArrayList<>();
        for (Seat seat : seatList) {
            String priceStr = inventoryService.getAreaPrice(sessionId, seat.getAreaId());
            if (priceStr == null) {
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
            BigDecimal price = new BigDecimal(priceStr);
            OrderItem item = new OrderItem();
            item.setOrderId(0L);
            item.setSeatId(seat.getId());
            item.setPrice(price);
            item.setSeatInfo(seat.getSeatName() != null ? seat.getSeatName()
                    : "row:" + seat.getRowNo() + ",col:" + seat.getColNo());
            items.add(item);
        }

        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setOrderNo(preGeneratedOrderNo != null
                ? preGeneratedOrderNo
                : String.valueOf(snowflake.nextId()));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(5));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        orderMapper.insert(order);
        for (OrderItem item : items) item.setOrderId(order.getId());
        orderItemMapper.batchInsert(items);

        // 事务内、INSERT 后的扩展点(派座模式用以把 task=SUCCESS 与 INSERT 原子提交)
        if (inTxnAfterInsert != null) {
            inTxnAfterInsert.run();
        }

        Long orderId = order.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitConsume.run();
                orderTimeoutProducer.sendTimeoutMessage(orderId);
            }
        });
        return order;
    }

    // ===================== 取消 =====================

    /**
     * 用户主动取消(写 cancel_reason=0)。
     * 事务从这里开始,以保证 cancelOrder 内的 registerSynchronization 有事务上下文。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long orderId) {
        cancelOrder(orderId, 0);
    }

    /** 超时自动取消(写 cancel_reason=1) */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByTimeout(Long orderId) {
        cancelOrder(orderId, 1);
    }

    /**
     * 内部辅助方法 — 由 cancelByUser/cancelByTimeout 在事务内调用。
     * 不要单独从外部直接调,否则 registerSynchronization 会抛 IllegalStateException。
     */
    private void cancelOrder(Long orderId, int cancelReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) {
            return;
        }

        int affected = orderMapper.cancelWithReason(orderId, cancelReason);
        if (affected == 0) return;

        List<OrderItem> items = orderItemMapper.selectByOrderId(orderId);
        Long sessionId = order.getSessionId();
        Long userId = order.getUserId();
        List<Long> seatIds = items.stream().map(OrderItem::getSeatId).collect(Collectors.toList());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                releaseSeatsByMode(sessionId, seatIds);
                purchaseLimitService.decrement(sessionId, userId, items.size());
            }
        });
    }

    // ===================== 退款 =====================

    /** 发起退款(已支付订单取消) */
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

    /** 按票号退单张票 */
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

        // 情侣座单票退款拦截 — 必须整对退,否则会破坏情侣对的成对结构(派座区一定要拦,
        // 选座区也拦以保持产品一致性:情侣座作为整体出售/退还)
        Seat seat = seatMapper.selectById(ticket.getSeatId());
        if (seat != null && seat.getType() != null && (seat.getType() == 2 || seat.getType() == 3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "情侣座必须整对退款,请使用整单退款");
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

        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        Set<Long> refundSet = new HashSet<>(refundSeatIds);
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
            log.error("退款 MQ 发送失败,回滚订单状态,orderId={}", order.getId(), e);
            orderMapper.updateStatusFrom(order.getId(), 3, order.getStatus());
            if (refundIncr.compareTo(BigDecimal.ZERO) > 0) {
                orderMapper.addRefundAmount(order.getId(), refundIncr.negate());
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "退款系统繁忙,请稍后重试");
        }

        try {
            releaseSeatsByMode(order.getSessionId(), refundSeatIds);
        } catch (Exception e) {
            log.error("退款座位释放失败,等待消费者兜底,orderId={},seatIds={}",
                    order.getId(), refundSeatIds, e);
        }
    }

    // ===================== 释放分流 =====================

    /**
     * 按 sale_mode 分流释放座位:
     *  - 选座区:releaseSeat(SADD 回全场 SET + 删 seat:lock + DECR locked 计数)
     *  - 派座区:releaseSeat 兜底(无 lock 不会出错) + releaseToPool(还库存 + 还池)
     *
     * 情侣座成对回池,绝不拆散。
     */
    public void releaseSeatsByMode(Long sessionId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) return;

        // 1. 加载 seats 拿到 area_id / type / pair_seat_id
        List<Seat> seats = seatMapper.selectByIds(seatIds);

        // 2. 全场 SET 统一恢复(选座+派座都需要)
        for (Long seatId : seatIds) {
            inventoryService.releaseSeat(sessionId, seatId);
        }

        // 3. 按 areaId 分组,查 sale_mode 决定是否需要还池
        Map<String, List<Seat>> byArea = seats.stream()
                .collect(Collectors.groupingBy(s -> s.getAreaId() == null ? "" : s.getAreaId()));

        for (Map.Entry<String, List<Seat>> entry : byArea.entrySet()) {
            String areaId = entry.getKey();
            SeatArea area = seatAreaMapper.selectBySessionAndArea(sessionId, areaId);
            if (area == null || area.getSaleMode() == null || area.getSaleMode() != 2) {
                continue; // 选座区已在 step 2 处理完毕
            }
            // 派座区:分单座/情侣对分别还池
            List<Seat> areaSeats = entry.getValue();
            List<Long> singles = new ArrayList<>();
            List<Long> couples = new ArrayList<>();
            Map<Long, long[]> pairInfo = new HashMap<>();
            for (Seat s : areaSeats) {
                if (s.getType() != null && s.getType() == 1) {
                    singles.add(s.getId());
                } else if (s.getType() != null && (s.getType() == 2 || s.getType() == 3)) {
                    couples.add(s.getId());
                    if (s.getPairSeatId() != null) {
                        pairInfo.put(s.getId(), new long[]{s.getId(), s.getPairSeatId()});
                    }
                }
            }
            if (!singles.isEmpty()) {
                allocationService.releaseSeatsToPool(sessionId, areaId,
                        SeatAllocationTask.TICKET_TYPE_SINGLE, singles, null);
            }
            if (!couples.isEmpty()) {
                allocationService.releaseSeatsToPool(sessionId, areaId,
                        SeatAllocationTask.TICKET_TYPE_COUPLE, couples, pairInfo);
            }
        }
    }
}
