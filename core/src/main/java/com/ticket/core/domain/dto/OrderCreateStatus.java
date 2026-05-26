package com.ticket.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 异步建单状态查询响应。
 *  - PROCESSING: 建单中,前端继续轮询
 *  - SUCCESS:    已落库, order 字段含完整订单详情供跳转
 *  - FAILED:     建单失败, message 含原因(座位被抢/价格异常等)
 *  - NOT_FOUND:  orderNo 不存在或 Redis 临时状态已过期且 DB 无记录,视为失败
 */
@Schema(description = "异步建单状态")
@Data
public class OrderCreateStatus {
    @Schema(description = "PROCESSING / SUCCESS / FAILED / NOT_FOUND") private String state;
    @Schema(description = "失败原因 / 提示信息") private String message;
    @Schema(description = "SUCCESS 时携带完整订单") private OrderStatusResponse order;

    public static OrderCreateStatus processing() {
        OrderCreateStatus s = new OrderCreateStatus();
        s.state = "PROCESSING";
        return s;
    }
    public static OrderCreateStatus success(OrderStatusResponse order) {
        OrderCreateStatus s = new OrderCreateStatus();
        s.state = "SUCCESS";
        s.order = order;
        return s;
    }
    public static OrderCreateStatus failed(String message) {
        OrderCreateStatus s = new OrderCreateStatus();
        s.state = "FAILED";
        s.message = message;
        return s;
    }
    public static OrderCreateStatus notFound() {
        OrderCreateStatus s = new OrderCreateStatus();
        s.state = "NOT_FOUND";
        s.message = "订单不存在或已过期";
        return s;
    }
}
