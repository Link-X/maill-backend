package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.vo.PageVO;
import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.core.service.OrderCommandService;
import com.ticket.core.service.OrderQueryService;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import com.ticket.user.dto.CancelOrderRequest;
import com.ticket.user.dto.OrderListRequest;
import com.ticket.user.dto.RefundTicketRequest;
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
@Tag(name = "订单（用户端）", description = "下单、取消、退款、查询。提交订单走 Redis Lua 原子锁座 + 同步建单；超时订单 5 分钟后由 MQ 自动取消")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final SessionService sessionService;
    private final PurchaseLimitService purchaseLimitService;
    private final SeatInventoryService inventoryService;
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    private static final long SEAT_LOCK_TTL = 300L;

    public OrderController(SessionService sessionService,
                           PurchaseLimitService purchaseLimitService,
                           SeatInventoryService inventoryService,
                           OrderCommandService orderCommandService,
                           OrderQueryService orderQueryService) {
        this.sessionService = sessionService;
        this.purchaseLimitService = purchaseLimitService;
        this.inventoryService = inventoryService;
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
    }

    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.USER,   limit = 5,   window = 60, message = "操作太频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @Operation(summary = "锁座 + 建单", description = "流程:实时时间/状态校验 → 限购校验 → Redis Lua 批量锁座(任一失败全量回滚) → 同步建单 → 发超时 MQ。直接返回完整订单(含演出/场次/座位/总价/过期时间),前端可显示倒计时。受多维度限流保护")
    @PostMapping("/submit")
    public Result<OrderStatusResponse> submit(@Valid @RequestBody SubmitOrderRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        var session = sessionService.getById(req.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 实时校验场次时间和状态,兜底定时任务 1 分钟精度窗口
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
        int seatCount = req.getSeatIds().size();
        // 限购 TTL 跟随场次结束时间,保证 key 在场次结束前不过期,防止限购被绕过
        long ttlSeconds = session.getEndTime() != null
                ? Math.max(0, ChronoUnit.SECONDS.between(now, session.getEndTime()))
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

        OrderCreateRequest orderReq = new OrderCreateRequest();
        orderReq.setUserId(userId);
        orderReq.setSessionId(req.getSessionId());
        orderReq.setSeatIds(req.getSeatIds());

        Order order = orderCommandService.createOrder(orderReq);
        return Result.success(orderQueryService.buildStatusResponse(order));
    }

    @Operation(summary = "取消订单", description = "status=0 未支付：同步取消，立即释放座位；status=1/5 已支付/部分退款：发起退款（异步，距演出开始 <24h 拒绝）；其他状态拒绝。仅订单本人可操作")
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
            // 未支付：直接同步取消，立即释放座位
            orderCommandService.cancelByUser(order.getId());
        } else if (order.getStatus() == 1 || order.getStatus() == 5) {
            // 已支付 或 部分退款：发起退款，退还剩余未使用票券，异步通过 MQ 处理
            orderCommandService.initiateRefund(order.getId());
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前订单状态不可取消");
        }
        return Result.success(null);
    }

    @Operation(summary = "订单详情", description = "返回完整 OrderStatusResponse（含演出名/场馆/城市/地址/票券列表）。仅订单本人可查")
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

    @Operation(summary = "单票退款", description = "支持已支付/部分退款订单。已核销或已退款的票拒绝；距演出开始 <24h 拒绝。多座位订单的单票退款会更新订单为部分退款状态")
    @PostMapping("/refundTicket")
    public Result<Void> refundTicket(@Valid @RequestBody RefundTicketRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        orderCommandService.initiateTicketRefund(req.getOrderNo(), req.getTicketNo(), userId);
        return Result.success(null);
    }

    @Operation(summary = "我的订单分页列表", description = "返回当前登录用户的订单。支持 status / 日期范围筛选。每条 item 是 OrderStatusResponse（含演出/场次/票券）")
    @PostMapping("/list")
    public Result<PageVO<OrderStatusResponse>> list(@Valid @RequestBody OrderListRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<OrderStatusResponse> orders = orderQueryService.getUserOrders(
                userId, req.getPage(), req.getSize(), req.getStatus(), req.getStartTime(), req.getEndTime());
        int total = orderQueryService.countUserOrders(userId, req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(PageVO.of(total, orders));
    }
}
