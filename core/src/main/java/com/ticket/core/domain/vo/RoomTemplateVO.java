package com.ticket.core.domain.vo;

import com.ticket.core.domain.entity.Room;
import com.ticket.core.domain.entity.RoomArea;
import com.ticket.core.domain.entity.RoomSeat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "场地模板聚合：一次返回场地 + 座位模板 + 默认价格区域；前端按 areaId 内存 join 渲染")
@Data
public class RoomTemplateVO {
    @Schema(description = "场地基本信息") private Room room;
    @Schema(description = "座位列表") private List<RoomSeat> seats;
    @Schema(description = "默认价格区域列表") private List<RoomArea> areas;
}
