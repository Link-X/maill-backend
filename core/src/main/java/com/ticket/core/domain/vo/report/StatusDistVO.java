package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "订单状态分布；返回固定 6 行（0..5），不存在的状态 count=0")
@Data
public class StatusDistVO {

    @Schema(description = "状态：0=待支付 1=已支付 2=已取消 3=退款中 4=已退款 5=部分退款", example = "1", allowableValues = {"0","1","2","3","4","5"})
    private Integer status;

    @Schema(description = "该状态订单数", example = "156")
    private Integer count;

    @Schema(description = "该状态下订单 totalAmount 之和；status=0/2 不是营收，仅供参考", example = "128400.00")
    private BigDecimal totalAmountSum;
}
