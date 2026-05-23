package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "演出维度的报表行")
@Data
public class ByShowVO {

    @Schema(description = "演出 ID", example = "1")
    private Long showId;

    @Schema(description = "演出名称", example = "周杰伦嘉年华世界巡回演唱会")
    private String showName;

    @Schema(description = "分类名（LEFT JOIN，可能为 null）", example = "演唱会")
    private String categoryName;

    @Schema(description = "城市名（LEFT JOIN city.code，可能为 null）", example = "上海")
    private String cityName;

    @Schema(description = "已支付订单数", example = "42")
    private Integer orderCount;

    @Schema(description = "已售票数", example = "168")
    private Integer ticketCount;

    @Schema(description = "营收（仅 status=1/5）", example = "53400.00")
    private BigDecimal revenue;

    @Schema(description = "退款金额", example = "880.00")
    private BigDecimal refundAmount;
}
