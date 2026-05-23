package com.ticket.admin.dto;

import com.ticket.core.domain.entity.RoomSeat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "批量保存场地座位模板（覆盖式）")
@Data
public class RoomSeatBatchRequest {
    @Schema(description = "场地 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场地ID不能为空") private Long roomId;

    @Schema(description = "座位列表；每个元素：rowNo / colNo / type / areaId / seatName / pairSeatId(情侣座才填)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "座位列表不能为空") private List<RoomSeat> seats;
}
