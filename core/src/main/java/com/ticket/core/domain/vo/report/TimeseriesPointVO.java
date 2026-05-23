package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;

/** /timeseries 每个点；date 格式取决于 dim：day=yyyy-MM-dd, hour=yyyy-MM-dd'T'HH, month=yyyy-MM */
@Data
public class TimeseriesPointVO {
    private String date;
    private Integer orderCount;
    private BigDecimal revenue;
    private BigDecimal refundAmount;
}
