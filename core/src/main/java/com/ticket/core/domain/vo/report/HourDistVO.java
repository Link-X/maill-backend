package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "24 小时下单分布；返回 0..23 共 24 行")
@Data
public class HourDistVO {
    @Schema(description = "小时 0..23", example = "20")
    private Integer hour;
    @Schema(description = "该时段下单数", example = "42")
    private Integer orderCount;
}
