package com.ticket.user.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class SubmitOrderRequest {
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    /** 单次下单最多 4 个座位,防止单请求锁定大量座位阻塞 Redis */
    @NotEmpty(message = "座位列表不能为空")
    @Size(max = 4, message = "单次下单最多 4 个座位")
    private List<Long> seatIds;
}
