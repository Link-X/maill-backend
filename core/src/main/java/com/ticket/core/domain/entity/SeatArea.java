package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "场次价格区域实体")
@Data
public class SeatArea {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "区域标识（场次内唯一）", example = "1") private String areaId;
    @Schema(description = "售价", example = "880.00") private BigDecimal price;
    @Schema(description = "原价", example = "1280.00") private BigDecimal originPrice;
}
