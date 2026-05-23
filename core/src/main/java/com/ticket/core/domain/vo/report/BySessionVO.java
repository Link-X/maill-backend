package com.ticket.core.domain.vo.report;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BySessionVO {
    private Long sessionId;
    private String showName;
    private String sessionName;
    private LocalDateTime startTime;
    private Integer totalSeats;
    /** 实际占座 = ticket WHERE status IN (0 未使用, 1 已核销)，已退款的不算 */
    private Integer soldSeats;
    /** = soldSeats / totalSeats */
    private Double fillRate;
    private BigDecimal revenue;
}
