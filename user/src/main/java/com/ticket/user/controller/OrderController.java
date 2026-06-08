package com.ticket.user.controller;

import com.ticket.common.constant.RedisKeys;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.dto.OrderAllocateMessage;
import com.ticket.core.domain.dto.OrderCreateMessage;
import com.ticket.core.domain.dto.OrderCreateStatus;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.dto.SubmitOrderResponse;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.SeatAllocationTask;
import com.ticket.core.domain.vo.PageVO;
import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.core.mapper.SeatAllocationTaskMapper;
import com.ticket.core.mq.producer.OrderAllocateProducer;
import com.ticket.core.mq.producer.OrderCreateProducer;
import com.ticket.core.service.OrderCommandService;
import com.ticket.core.service.OrderQueryService;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatAllocationService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import com.ticket.user.dto.CancelOrderRequest;
import com.ticket.user.dto.OrderListRequest;
import com.ticket.user.dto.RefundTicketRequest;
import com.ticket.user.dto.SubmitByAreaRequest;
import com.ticket.user.dto.SubmitOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Tag(name = "订单(用户端)",
     description = "支持两种下单模式:\n" +
                   " - /submit/by-seats : 选座模式,前端传具体 seatIds(VIP/前排区域常用)\n" +
                   " - /submit/by-area  : 派座模式,前端传 areaId+ticketType+quantity,系统派座(普通看台常用)\n" +
                   "两个接口都是异步建单,返回 orderNo 后前端轮询 /createStatus 直到 SUCCESS/FAILED。")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final SessionService sessionService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatInventoryService inventoryService;
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final OrderCreateProducer orderCreateProducer;
    private final OrderAllocateProducer orderAllocateProducer;
    private final SeatAllocationService allocationService;
    private final SeatAllocationTaskMapper allocationTaskMapper;
    private final SnowflakeIdGenerator snowflake;
    private final StringRedisTemplate redisTemplate;

    private static final long SEAT_LOCK_TTL = 300L;
    private static final Duration PENDING_TTL = Duration.ofSeconds(60);

    public OrderController(SessionService sessionService,
                           PurchaseLimitService purchaseLimitService,
                           SeatInventoryService inventoryService,
                           OrderCommandService orderCommandService,
                           OrderQueryService orderQueryService,
                           OrderCreateProducer orderCreateProducer,
                           OrderAllocateProducer orderAllocateProducer,
                           SeatAllocationService allocationService,
                           SeatAllocationTaskMapper allocationTaskMapper,
                           SnowflakeIdGenerator snowflake,
                           StringRedisTemplate redisTemplate) {
        this.sessionService = sessionService;
        this.purchaseLimitService = purchaseLimitService;
        this.inventoryService = inventoryService;
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
        this.orderCreateProducer = orderCreateProducer;
        this.orderAllocateProducer = orderAllocateProducer;
        this.allocationService = allocationService;
        this.allocationTaskMapper = allocationTaskMapper;
        this.snowflake = snowflake;
        this.redisTemplate = redisTemplate;
    }

    // ===================== 入口 A:选座模式 =====================

    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁,请稍后再试")
    @RateLimit(type = LimitType.USER,   limit = 5,   window = 60, message = "操作太频繁,请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙,请稍后重试")
    @Operation(summary = "锁座下单(选座模式)",
            description = "前端传具体 seatIds,后端做校验+限购+Lua 锁座+预生成 orderNo+发建单 MQ → 立即返回。" +
                          "建单 INSERT 由 OrderCreateConsumer 异步落库,前端拿 orderNo 调 /createStatus 轮询。")
    @PostMapping("/submit/by-seats")
    public Result<SubmitOrderResponse> submitBySeats(@Valid @RequestBody SubmitOrderRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var session = sessionService.getById(req.getSessionId());
        validateSessionOnSale(session);

        int seatCount = req.getSeatIds().size();
        long ttlSeconds = session.getEndTime() != null
                ? Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), session.getEndTime()))
                : 0;
        boolean allowed = purchaseLimitService.checkAndIncrement(
                req.getSessionId(), userId, session.getLimitPerUser(), seatCount, ttlSeconds);
        if (!allowed) {
            throw new BusinessException(ErrorCode.EXCEED_PURCHASE_LIMIT);
        }

        boolean locked = inventoryService.batchLockSeats(
                req.getSessionId(), req.getSeatIds(), String.valueOf(userId), SEAT_LOCK_TTL);
        if (!locked) {
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }

        String orderNo = String.valueOf(snowflake.nextId());
        redisTemplate.opsForValue().set(
                RedisKeys.orderCreatePending(orderNo), "PROCESSING", PENDING_TTL);

        try {
            orderCreateProducer.sendCreateMessage(
                    new OrderCreateMessage(orderNo, userId, req.getSessionId(), req.getSeatIds()));
        } catch (Exception e) {
            for (Long seatId : req.getSeatIds()) {
                inventoryService.releaseSeat(req.getSessionId(), seatId);
            }
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            log.error("发送选座建单 MQ 失败, orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙,请重试");
        }

        SubmitOrderResponse resp = new SubmitOrderResponse();
        resp.setOrderNo(orderNo);
        resp.setStatus("PROCESSING");
        return Result.success(resp);
    }

    /** 兼容旧路径 /submit 为选座模式 */
    @Operation(summary = "[兼容] 选座下单旧路径", description = "等价于 /submit/by-seats,保留供旧客户端使用")
    @PostMapping("/submit")
    public Result<SubmitOrderResponse> submit(@Valid @RequestBody SubmitOrderRequest req) {
        return submitBySeats(req);
    }

    // ===================== 入口 B:派座模式 =====================

    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁,请稍后再试")
    @RateLimit(type = LimitType.USER,   limit = 5,   window = 60, message = "操作太频繁,请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙,请稍后重试")
    @Operation(summary = "派座下单(派座模式)",
            description = "前端传 areaId+ticketType+quantity,后端做校验+限购+Redis 原子扣库存+预生成 orderNo+" +
                          "写 task=PENDING+发派座 MQ → 立即返回。具体派座(从池子取 N 个座位)+建单 INSERT 由 " +
                          "OrderAllocateConsumer 异步执行。\n\n" +
                          "情侣保护:ticketType=1 只能派 type=1 的座位,ticketType=2 只能派成对的情侣座 — " +
                          "由 warmup 阶段的池子物理隔离保证,不可能跨型派错。")
    @PostMapping("/submit/by-area")
    public Result<SubmitOrderResponse> submitByArea(@Valid @RequestBody SubmitByAreaRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var session = sessionService.getById(req.getSessionId());
        validateSessionOnSale(session);

        // 实际座位数 = quantity * (情侣?2:1),限购按张数计
        int seatCount = req.getTicketType() == SeatAllocationTask.TICKET_TYPE_COUPLE
                ? req.getQuantity() * 2
                : req.getQuantity();

        long ttlSeconds = session.getEndTime() != null
                ? Math.max(0, ChronoUnit.SECONDS.between(LocalDateTime.now(), session.getEndTime()))
                : 0;
        boolean allowed = purchaseLimitService.checkAndIncrement(
                req.getSessionId(), userId, session.getLimitPerUser(), seatCount, ttlSeconds);
        if (!allowed) {
            throw new BusinessException(ErrorCode.EXCEED_PURCHASE_LIMIT);
        }

        // 同步扣库存(校验 sale_mode + 区域是否存在该票种 + 库存原子扣减)
        SeatAllocationService.StockReservation reservation;
        try {
            reservation = allocationService.reserveStock(
                    req.getSessionId(), req.getAreaId(), req.getTicketType(), req.getQuantity());
        } catch (BusinessException e) {
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            throw e;
        }

        String orderNo = String.valueOf(snowflake.nextId());

        // 写派座任务表(中间状态持久化,定时回滚扫描器据此恢复)
        SeatAllocationTask task = new SeatAllocationTask();
        task.setOrderNo(orderNo);
        task.setUserId(userId);
        task.setSessionId(req.getSessionId());
        task.setAreaId(req.getAreaId());
        task.setTicketType(req.getTicketType());
        task.setQuantity(req.getQuantity());
        task.setStatus(SeatAllocationTask.STATUS_PENDING);
        try {
            allocationTaskMapper.insert(task);
        } catch (Exception e) {
            allocationService.rollbackStockOnly(reservation.sessionId, reservation.areaId,
                    reservation.ticketType, reservation.quantity);
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            log.error("写派座任务表失败, orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙,请重试");
        }

        redisTemplate.opsForValue().set(
                RedisKeys.orderCreatePending(orderNo), "PROCESSING", PENDING_TTL);

        try {
            orderAllocateProducer.sendAllocateMessage(new OrderAllocateMessage(orderNo));
        } catch (Exception e) {
            // MQ 失败:库存回滚 + 退限购 + task 直接置为 ROLLED_BACK + 清 pending
            allocationService.rollbackStockOnly(reservation.sessionId, reservation.areaId,
                    reservation.ticketType, reservation.quantity);
            purchaseLimitService.decrement(req.getSessionId(), userId, seatCount);
            allocationTaskMapper.updateStatusFrom(orderNo,
                    SeatAllocationTask.STATUS_PENDING,
                    SeatAllocationTask.STATUS_ROLLED_BACK,
                    null, "MQ 发送失败");
            redisTemplate.delete(RedisKeys.orderCreatePending(orderNo));
            log.error("发送派座建单 MQ 失败, orderNo={}", orderNo, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙,请重试");
        }

        SubmitOrderResponse resp = new SubmitOrderResponse();
        resp.setOrderNo(orderNo);
        resp.setStatus("PROCESSING");
        return Result.success(resp);
    }

    private void validateSessionOnSale(com.ticket.core.domain.entity.ShowSession session) {
        if (session == null) throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now();
        if (session.getEndTime() != null && !now.isBefore(session.getEndTime())) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_ENDED);
        }
        if (session.getOpenSaleTime() != null && now.isBefore(session.getOpenSaleTime())) {
            throw new BusinessException(ErrorCode.SESSION_NOT_ON_SALE);
        }
        if (session.getStatus() == null || session.getStatus() != 1) {
            throw new BusinessException(ErrorCode.SESSION_NOT_ON_SALE);
        }
    }

    // ===================== 公共:状态查询、取消、退款、列表 =====================

    @Operation(summary = "异步建单状态查询",
            description = "前端 submit 后用返回的 orderNo 轮询本接口,直到 state=SUCCESS 或 FAILED。" +
                          "选座和派座两种模式共用本接口。推荐 500ms~1s 间隔,最长 30s 超时。")
    @GetMapping("/createStatus")
    public Result<OrderCreateStatus> createStatus(
            @Parameter(description = "submit 返回的 orderNo", required = true) @RequestParam String orderNo) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        OrderCreateStatus status = orderQueryService.getCreateStatus(orderNo);
        if ("SUCCESS".equals(status.getState()) && status.getOrder() != null
                && status.getOrder().getOrderId() != null) {
            Order order = orderQueryService.getById(status.getOrder().getOrderId());
            if (order != null && !order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        return Result.success(status);
    }

    @Operation(summary = "取消订单", description = "status=0 未支付:同步取消;status=1/5 已支付/部分退款:发起退款。仅订单本人可操作")
    @PostMapping("/cancel")
    public Result<Void> cancel(@Valid @RequestBody CancelOrderRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Order order = orderQueryService.getByOrderNo(req.getOrderNo());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (order.getStatus() == 0) {
            orderCommandService.cancelByUser(order.getId());
        } else if (order.getStatus() == 1 || order.getStatus() == 5) {
            orderCommandService.initiateRefund(order.getId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前订单状态不可取消");
        }
        return Result.success(null);
    }

    @Operation(summary = "订单详情", description = "返回完整 OrderStatusResponse。仅订单本人可查")
    @GetMapping("/orderDetails")
    public Result<OrderStatusResponse> detail(
            @Parameter(description = "订单号 orderNo", required = true) @RequestParam String orderNo) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Order order = orderQueryService.getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.success(orderQueryService.buildStatusResponse(order));
    }

    @Operation(summary = "单票退款",
            description = "支持已支付/部分退款订单。已核销/已退款的票拒绝;距演出开始 <24h 拒绝;情侣座必须整对退款")
    @PostMapping("/refundTicket")
    public Result<Void> refundTicket(@Valid @RequestBody RefundTicketRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        orderCommandService.initiateTicketRefund(req.getOrderNo(), req.getTicketNo(), userId);
        return Result.success(null);
    }

    @Operation(summary = "我的订单分页列表",
            description = "返回当前登录用户的订单。支持 status / 日期范围筛选。每条 item 是 OrderStatusResponse")
    @PostMapping("/list")
    public Result<PageVO<OrderStatusResponse>> list(@Valid @RequestBody OrderListRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<OrderStatusResponse> orders = orderQueryService.getUserOrders(
                userId, req.getPage(), req.getSize(), req.getStatus(), req.getStartTime(), req.getEndTime());
        int total = orderQueryService.countUserOrders(userId, req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(PageVO.of(total, orders));
    }
}
