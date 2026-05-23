package com.ticket.admin.controller;

import com.ticket.admin.dto.RoomAreaSaveRequest;
import com.ticket.admin.dto.RoomCreateRequest;
import com.ticket.admin.dto.RoomSeatBatchRequest;
import com.ticket.admin.dto.RoomUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Room;
import com.ticket.core.domain.entity.RoomArea;
import com.ticket.core.domain.entity.RoomSeat;
import com.ticket.core.domain.vo.RoomTemplateVO;
import com.ticket.core.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "场地模板", description = "场地是座位布局与价格区域的模板，创建场次时传 roomId 后端会自动复制座位和默认价格区域到该场次；同一场地可被多场次复用")
@RestController
@RequestMapping("/api/admin/room")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "创建场地", description = "新建一个空场地，座位模板和价格区域随后通过 /seat/batch 和 /area/save 保存")
    @PostMapping("/create")
    public Result<Room> createRoom(@Valid @RequestBody RoomCreateRequest req) {
        Room room = new Room();
        room.setName(req.getName());
        room.setVenue(req.getVenue());
        room.setRowCount(req.getRowCount());
        room.setColCount(req.getColCount());
        room.setDescription(req.getDescription());
        return Result.success(roomService.createRoom(room));
    }

    @Operation(summary = "更新场地基本信息", description = "改的是场地本身，不影响已经基于此场地创建的场次（场次的座位/价格已经在创建时复制走了，独立维护）")
    @PutMapping("/update")
    public Result<Room> updateRoom(@Valid @RequestBody RoomUpdateRequest req) {
        Room room = new Room();
        room.setId(req.getId());
        room.setName(req.getName());
        room.setVenue(req.getVenue());
        room.setRowCount(req.getRowCount());
        room.setColCount(req.getColCount());
        room.setDescription(req.getDescription());
        return Result.success(roomService.updateRoom(room));
    }

    @Operation(summary = "场地详情")
    @GetMapping("/{id}")
    public Result<Room> getRoom(@Parameter(description = "场地 ID") @PathVariable Long id) {
        return Result.success(roomService.getRoom(id));
    }

    @Operation(summary = "场地列表", description = "返回所有场地（不分页，因为场地数量通常很小）")
    @GetMapping("/list")
    public Result<List<Room>> listRooms() {
        return Result.success(roomService.listRooms());
    }

    @Operation(summary = "保存场地座位模板（覆盖写）", description = "覆盖式：把当前 roomId 下所有 room_seat 删掉，重新插入。type=1 普通 / 2 情侣左 / 3 情侣右；情侣座必须成对出现且 pairSeatId 互相指")
    @PostMapping("/seat/batch")
    public Result<Void> saveSeats(@Valid @RequestBody RoomSeatBatchRequest req) {
        roomService.saveSeats(req.getRoomId(), req.getSeats());
        return Result.success();
    }

    @Operation(summary = "场地座位模板列表", description = "返回该场地的所有座位（含坐标/类型/区域）")
    @GetMapping("/seat/list")
    public Result<List<RoomSeat>> listSeats(@Parameter(description = "场地 ID") @RequestParam Long roomId) {
        return Result.success(roomService.listSeats(roomId));
    }

    @Operation(summary = "保存场地默认价格区域（覆盖写）", description = "areaId 是字符串 ID，与 room_seat.areaId 对应；创建场次时这些默认价格会被复制到 seat_area，之后可在场次维度单独覆盖")
    @PostMapping("/area/save")
    public Result<Void> saveAreas(@Valid @RequestBody RoomAreaSaveRequest req) {
        roomService.saveAreas(req.getRoomId(), req.getAreas());
        return Result.success();
    }

    @Operation(summary = "场地默认价格区域列表")
    @GetMapping("/area/list")
    public Result<List<RoomArea>> listAreas(@Parameter(description = "场地 ID") @RequestParam Long roomId) {
        return Result.success(roomService.listAreas(roomId));
    }

    @Operation(summary = "场地完整模板（聚合）", description = "一次返回 room 基本信息 + 座位模板 + 默认价格区域；前端用 areaId 在内存做 join 渲染座位图，省一次往返")
    @GetMapping("/template")
    public Result<RoomTemplateVO> getRoomTemplate(@Parameter(description = "场地 ID") @RequestParam Long roomId) {
        return Result.success(roomService.getRoomTemplate(roomId));
    }
}
