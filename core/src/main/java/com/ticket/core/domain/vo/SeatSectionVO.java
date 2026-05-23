package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Schema(description = "座位网格")
@Data
public class SeatSectionVO {
    @Schema(description = "总行数", example = "20") private Integer rowCount;
    @Schema(description = "总列数", example = "20") private Integer columnCount;
    @Schema(description = "行列表") private List<SeatRowVO> seatRows;
}
