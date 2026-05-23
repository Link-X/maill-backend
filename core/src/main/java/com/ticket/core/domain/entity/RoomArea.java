package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "场地默认价格区域（创建场次时复制为 seat_area）")
@Data
public class RoomArea {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "场地 ID", example = "1") private Long roomId;
    @Schema(description = "区域 ID（字符串，与 room_seat.areaId 对应）", example = "1") private String areaId;
    @Schema(description = "默认售价", example = "880.00") private BigDecimal defaultPrice;
    @Schema(description = "默认原价", example = "1280.00") private BigDecimal defaultOriginPrice;
}
