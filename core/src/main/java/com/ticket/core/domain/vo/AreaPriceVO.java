package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "场次价格区域(用于座位图色块、图例,以及前端分流选座/派座 UI)")
@Data
public class AreaPriceVO {
    @Schema(description = "区域 ID(字符串,与 seat.areaId 对应)", example = "1") private String areaId;
    @Schema(description = "售价", example = "880.00") private String price;
    @Schema(description = "原价", example = "1280.00") private String originPrice;

    @Schema(description = "售卖模式:1=用户选座,2=系统派座", example = "1",
            allowableValues = {"1", "2"})
    private Integer saleMode;

    @Schema(description = "区域内单座总数(type=1);派座区前端展示'剩余 X/Y'用", example = "100")
    private Integer singleTotal;

    @Schema(description = "区域内情侣对总数(type=2+3 成对);派座区前端用", example = "20")
    private Integer coupleTotal;

    @Schema(description = "单座当前剩余数(派座区实时读 Redis area:stock:single);选座区为 null",
            example = "98")
    private Integer singleStock;

    @Schema(description = "情侣对当前剩余数(派座区实时读 Redis area:stock:couple);选座区为 null",
            example = "20")
    private Integer coupleStock;
}
