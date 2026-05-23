package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Schema(description = "提交订单（锁座 + 建单）")
@Data
public class SubmitOrderRequest {
    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场次ID不能为空") private Long sessionId;

    @Schema(description = "座位 ID 列表；单次下单最多 4 个（防止单请求锁定大量座位阻塞 Redis）", example = "[101, 102]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "座位列表不能为空") @Size(max = 4, message = "单次下单最多 4 个座位") private List<Long> seatIds;
}
