package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Schema(description = "座位网格的一行")
@Data
public class SeatRowVO {
    @Schema(description = "行 ID（字符串）", example = "1") private String rowsId;
    @Schema(description = "行号显示文本", example = "1") private String rowsNum;
    @Schema(description = "该行的所有列") private List<SeatColVO> columns;
}
