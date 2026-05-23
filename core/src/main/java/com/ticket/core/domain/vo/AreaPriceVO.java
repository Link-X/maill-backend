package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "场次价格区域（用于座位图色块和图例）")
@Data
public class AreaPriceVO {
    @Schema(description = "区域 ID（字符串，与 seat.areaId 对应）", example = "1") private String areaId;
    @Schema(description = "售价", example = "880.00") private String price;
    @Schema(description = "原价", example = "1280.00") private String originPrice;
}
