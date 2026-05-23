package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ByCategoryVO {
    private Long categoryId;
    private String categoryName;
    private Integer orderCount;
    private Integer ticketCount;
    private BigDecimal revenue;
}
