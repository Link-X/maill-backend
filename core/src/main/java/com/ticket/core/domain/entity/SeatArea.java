package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "场次价格区域实体（含售卖模式）")
@Data
public class SeatArea {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "区域标识（场次内唯一）", example = "1") private String areaId;
    @Schema(description = "售价", example = "880.00") private BigDecimal price;
    @Schema(description = "原价", example = "1280.00") private BigDecimal originPrice;

    @Schema(description = "售卖模式: 1=用户选座, 2=系统派座", example = "1",
            allowableValues = {"1", "2"})
    private Integer saleMode;

    @Schema(description = "区域内单座总数(type=1),初始化时按 seat 表统计", example = "100")
    private Integer singleTotal;

    @Schema(description = "区域内情侣对总数(type=2+3 成对),初始化时按 seat 表统计", example = "20")
    private Integer coupleTotal;

    @Schema(description = "派座策略(sale_mode=2 生效): 1=连坐优先, 2=分散, 3=任意", example = "1",
            allowableValues = {"1", "2", "3"})
    private Integer allocateStrategy;
}
