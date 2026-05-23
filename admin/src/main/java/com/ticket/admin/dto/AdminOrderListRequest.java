package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 管理端订单列表查询参数
 * 所有筛选字段均可选；showId / sessionId / orderNo / status / 时间范围。
 */
@Data
public class AdminOrderListRequest {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer page;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    private Integer size;

    private Long showId;
    private Long sessionId;
    private String orderNo;
    /** 0=待支付 1=已支付 2=已取消 3=退款中 4=已退款 5=部分退款 */
    private Integer status;
    /** 按订单 createTime 筛选 */
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
