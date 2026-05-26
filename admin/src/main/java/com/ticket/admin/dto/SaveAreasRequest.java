package com.ticket.admin.dto;

import com.ticket.core.domain.entity.SeatArea;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "保存/覆盖场次价格区域")
@Data
public class SaveAreasRequest {
    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull private Long sessionId;
    @Schema(description = "价格区域列表；每元素：areaId / price / originPrice", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty private List<SeatArea> areas;
}
