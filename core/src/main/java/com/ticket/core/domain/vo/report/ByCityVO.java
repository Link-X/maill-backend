package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ByCityVO {
    private String cityCode;
    private String cityName;
    private Integer orderCount;
    private Integer ticketCount;
    private BigDecimal revenue;
}
