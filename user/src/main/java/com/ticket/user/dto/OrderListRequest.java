package com.ticket.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

@Data
public class OrderListRequest {
    @Min(value = 1, message = "页码不能小于 1")
    private int page = 1;

    /** 单页最多 50 条,防止用户传超大值导致 OOM */
    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 50, message = "每页数量不能超过 50")
    private int size = 10;

    /** 订单状态：0-待支付 1-已支付 2-已取消 3-退款中 4-已退款，不传则查全部 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
