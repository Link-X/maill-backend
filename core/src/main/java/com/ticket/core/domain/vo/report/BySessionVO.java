package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "场次售罄率")
@Data
public class BySessionVO {
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "演出名", example = "周杰伦演唱会") private String showName;
    @Schema(description = "场次名", example = "上海 2026-06-01") private String sessionName;
    @Schema(description = "开演时间") private LocalDateTime startTime;
    @Schema(description = "总座位", example = "400") private Integer totalSeats;
    @Schema(description = "实际占座 = ticket.status IN (0,1)；已退款不算", example = "356") private Integer soldSeats;
    @Schema(description = "售罄率 0~1", example = "0.89") private Double fillRate;
    @Schema(description = "营收", example = "53400.00") private BigDecimal revenue;
}
