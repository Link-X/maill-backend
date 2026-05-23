package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "城市维度的报表行")
@Data
public class ByCityVO {

    @Schema(description = "GB/T 行政区划代码", example = "110000", nullable = true)
    private String cityCode;

    @Schema(description = "城市名", example = "北京", nullable = true)
    private String cityName;

    @Schema(description = "已支付订单数", example = "80")
    private Integer orderCount;

    @Schema(description = "已售票数", example = "320")
    private Integer ticketCount;

    @Schema(description = "营收", example = "102400.00")
    private BigDecimal revenue;
}
