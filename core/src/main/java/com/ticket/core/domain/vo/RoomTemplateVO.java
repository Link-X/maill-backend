package com.ticket.core.domain.vo;

import com.ticket.core.domain.entity.Room;
import com.ticket.core.domain.entity.RoomArea;
import com.ticket.core.domain.entity.RoomSeat;
import lombok.Data;

import java.util.List;

/**
 * 场地模板聚合视图：一次返回场地基本信息、座位模板、默认价格区域，
 * 供前端编辑/预览页面使用，避免分别调 /seat/list 和 /area/list 后再 join。
 */
@Data
public class RoomTemplateVO {
    private Room room;
    private List<RoomSeat> seats;
    private List<RoomArea> areas;
}
