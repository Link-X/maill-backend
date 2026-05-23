package com.ticket.admin.dto;

import com.ticket.core.domain.entity.RoomArea;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "保存场地默认价格区域（覆盖式）")
@Data
public class RoomAreaSaveRequest {
    @Schema(description = "场地 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long roomId;
    @Schema(description = "价格区域列表；每元素：areaId（字符串，与 seat.areaId 对应）/ defaultPrice / defaultOriginPrice", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty private List<RoomArea> areas;
}
