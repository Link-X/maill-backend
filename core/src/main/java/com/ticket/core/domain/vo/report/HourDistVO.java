package com.ticket.core.domain.vo.report;

import lombok.Data;

@Data
public class HourDistVO {
    /** 0..23 */
    private Integer hour;
    private Integer orderCount;
}
