package com.ticket.admin.controller;

import com.ticket.admin.dto.AdminOrderListRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.dto.OrderStatusResponse;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.service.OrderQueryService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/order")
public class OrderController {

    private final OrderMapper orderMapper;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderMapper orderMapper, OrderQueryService orderQueryService) {
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/{id}")
    public Result<?> getOrder(@PathVariable Long id) {
        return Result.success(orderMapper.selectById(id));
    }

    @GetMapping("/query")
    public Result<?> queryByOrderNo(@RequestParam String orderNo) {
        return Result.success(orderQueryService.getByOrderNo(orderNo));
    }

    @GetMapping("/{id}/items")
    public Result<?> getOrderItems(@PathVariable Long id) {
        return Result.success(orderQueryService.getOrderItems(id));
    }

    /**
     * 管理端订单列表（分页）
     * 筛选维度：showId / sessionId / orderNo / status / 时间范围
     * 返回与用户端 /api/order/list 同结构（OrderStatusResponse，含 show / city / tickets 等）
     */
    @PostMapping("/list")
    public Result<?> list(@Valid @RequestBody AdminOrderListRequest req) {
        List<OrderStatusResponse> orders = orderQueryService.getAdminOrders(
                req.getPage(), req.getSize(),
                req.getShowId(), req.getSessionId(), req.getOrderNo(),
                req.getStatus(), req.getStartTime(), req.getEndTime());
        int total = orderQueryService.countAdminOrders(
                req.getShowId(), req.getSessionId(), req.getOrderNo(),
                req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(Map.of("total", total, "list", orders));
    }
}
