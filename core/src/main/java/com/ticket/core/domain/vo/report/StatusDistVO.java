package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StatusDistVO {
    /** 0=待支付 1=已支付 2=已取消 3=退款中 4=已退款 5=部分退款 */
    private Integer status;
    private Integer count;
    /** 该状态下订单 totalAmount 之和；0/2 状态非营收，仅参考 */
    private BigDecimal totalAmountSum;
}
