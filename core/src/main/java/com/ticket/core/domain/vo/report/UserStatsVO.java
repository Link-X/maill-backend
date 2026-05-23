package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UserStatsVO {
    /** 下过单（status=1/5）的去重用户数 */
    private Integer totalBuyers;
    /** 同期内购买 >=2 次的用户数 */
    private Integer repeatBuyers;
    /** = repeatBuyers / totalBuyers */
    private Double repeatRate;
    /** 客单价 = revenue / orderCount */
    private BigDecimal avgOrderValue;
    /** 平均座位数 = ticketSoldCount / orderCount */
    private Double avgSeatsPerOrder;
}
