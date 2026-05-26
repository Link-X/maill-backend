package com.ticket.admin.controller;

import com.ticket.admin.dto.AdminOrderListRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.OrderItem;
import com.ticket.core.domain.vo.PageVO;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "订单管理（管理端）", description = "管理员视角的订单查询；列表与详情都自动 batch-load 演出/场次/城市/票券，避免 N+1")
@RestController
@RequestMapping("/api/admin/order")
public class OrderController {

    private final OrderMapper orderMapper;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderMapper orderMapper, OrderQueryService orderQueryService) {
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
    }

    @Operation(summary = "按 id 获取原始订单", description = "返回 Order 实体，仅含订单本身字段（不带演出/票券信息）。需要完整信息请用 /list 或 /query")
    @GetMapping("/{id}")
    public Result<Order> getOrder(@Parameter(description = "订单数据库自增 id") @PathVariable Long id) {
        return Result.success(orderMapper.selectById(id));
    }

    @Operation(summary = "按订单号查询", description = "通过 orderNo（雪花 ID 字符串）精确查找")
    @GetMapping("/query")
    public Result<Order> queryByOrderNo(
            @Parameter(description = "订单号 orderNo（雪花 ID）", required = true) @RequestParam String orderNo) {
        return Result.success(orderQueryService.getByOrderNo(orderNo));
    }

    @Operation(summary = "订单明细", description = "返回该订单所有 order_item（含座位 ID、单价、座位信息字符串）")
    @GetMapping("/{id}/items")
    public Result<List<OrderItem>> getOrderItems(@Parameter(description = "订单 id") @PathVariable Long id) {
        return Result.success(orderQueryService.getOrderItems(id));
    }

    @Operation(summary = "订单分页列表", description = "支持 showId / sessionId / orderNo / status / 时间范围筛选。"
            + "返回结构与用户端 /api/order/list 一致（OrderStatusResponse，包含 show 名称、城市、座位列表、票券列表）。"
            + "list 内部已做批量预取，N 个订单只查 4-5 次 DB")
    @PostMapping("/list")
    public Result<PageVO<OrderStatusResponse>> list(@Valid @RequestBody AdminOrderListRequest req) {
        List<OrderStatusResponse> orders = orderQueryService.getAdminOrders(
                req.getPage(), req.getSize(),
                req.getShowId(), req.getSessionId(), req.getOrderNo(),
                req.getStatus(), req.getStartTime(), req.getEndTime());
        int total = orderQueryService.countAdminOrders(
                req.getShowId(), req.getSessionId(), req.getOrderNo(),
                req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(PageVO.of(total, orders));
    }
}
