package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "管理端订单列表查询参数；所有筛选字段均可选")
@Data
public class AdminOrderListRequest {

    @Schema(description = "页码，从 1 开始", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "页码不能为空") @Min(value = 1, message = "页码最小为1")
    private Integer page;

    @Schema(description = "每页条数，1-100", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "每页条数不能为空") @Min(value = 1, message = "每页条数最小为1") @Max(value = 100, message = "每页条数最大为100")
    private Integer size;

    @Schema(description = "演出 ID（按 show 筛选；后端 JOIN show_session 解析）", example = "1") private Long showId;
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "订单号精确匹配", example = "704179544755671040") private String orderNo;
    @Schema(description = "订单状态 0/1/2/3/4/5", example = "1", allowableValues = {"0","1","2","3","4","5"}) private Integer status;
    @Schema(description = "起始时间（按订单 createTime 筛选）", example = "2026-05-01T00:00:00") private LocalDateTime startTime;
    @Schema(description = "结束时间", example = "2026-05-31T23:59:59") private LocalDateTime endTime;
}
