package com.ticket.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

@Schema(description = "我的订单分页查询")
@Data
public class OrderListRequest {
    @Schema(description = "页码（从 1 开始）", example = "1") @Min(value = 1) private int page = 1;
    @Schema(description = "每页条数 1-50", example = "10") @Min(value = 1) @Max(value = 50) private int size = 10;
    @Schema(description = "订单状态 0/1/2/3/4/5；不传查全部", example = "1") private Integer status;
    @Schema(description = "起始时间", example = "2026-05-01 00:00:00") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime startTime;
    @Schema(description = "结束时间", example = "2026-05-31 23:59:59") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime endTime;
}
