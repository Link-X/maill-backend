package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "场地座位模板（创建场次时被复制为 seat）")
@Data
public class RoomSeat {
    @Schema(description = "座位 ID", example = "1") private Long id;
    @Schema(description = "场地 ID", example = "1") private Long roomId;
    @Schema(description = "排号（从 1 开始）", example = "1") private Integer rowNo;
    @Schema(description = "列号（从 1 开始）", example = "1") private Integer colNo;
    @Schema(description = "座位类型 1=普通 2=情侣左 3=情侣右", example = "1", allowableValues = {"1","2","3"})
    private Integer type;
    @Schema(description = "价格区域 ID", example = "1") private String areaId;
    @Schema(description = "座位名称", example = "1排01座") private String seatName;
    @Schema(description = "情侣连座配对座位 ID（type=2/3 才有值）") private Long pairSeatId;
}
