package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "分类维度的报表行")
@Data
public class ByCategoryVO {

    @Schema(description = "分类 ID；未关联分类的演出会归到 null 分类一行", example = "1", nullable = true)
    private Long categoryId;

    @Schema(description = "分类名", example = "演唱会", nullable = true)
    private String categoryName;

    @Schema(description = "已支付订单数", example = "120")
    private Integer orderCount;

    @Schema(description = "已售票数", example = "480")
    private Integer ticketCount;

    @Schema(description = "营收", example = "152800.00")
    private BigDecimal revenue;
}
