package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ByShowVO {
    private Long showId;
    private String showName;
    private String categoryName;
    private String cityName;
    private Integer orderCount;
    private Integer ticketCount;
    private BigDecimal revenue;
    private BigDecimal refundAmount;
}
