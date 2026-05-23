package com.ticket.admin.dto;

import com.ticket.core.domain.entity.Seat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "手动批量创建场次座位（不走场地模板时使用）")
@Data
public class BatchCreateSeatRequest {
    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long sessionId;
    @Schema(description = "座位列表；每元素：rowNo/colNo/type/areaId/seatName/pairSeatId（情侣座）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty private List<Seat> seats;
}
