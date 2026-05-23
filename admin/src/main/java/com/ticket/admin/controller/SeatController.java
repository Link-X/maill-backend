package com.ticket.admin.controller;

import com.ticket.admin.dto.BatchCreateSeatRequest;
import com.ticket.admin.dto.SaveAreasRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.service.SeatAreaService;
import com.ticket.core.service.SeatInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "座位（场次维度）", description = "不走场地模板（手动模式）时使用：直接给特定 sessionId 批量建座位、设价格、预热 Redis 库存。走模板路径时只需调 /warmup")
@RestController
@RequestMapping("/api/admin/seat")
public class SeatController {

    private final SeatMapper seatMapper;
    private final SeatInventoryService inventoryService;
    private final SeatAreaService seatAreaService;

    public SeatController(SeatMapper seatMapper,
                          SeatInventoryService inventoryService,
                          SeatAreaService seatAreaService) {
        this.seatMapper = seatMapper;
        this.inventoryService = inventoryService;
        this.seatAreaService = seatAreaService;
    }

    @Operation(summary = "批量创建场次座位（手动模式）", description = "不走场地模板时使用。seats 中每个元素需提供: rowNo, colNo, type, areaId, seatName, pairSeatId(情侣座才填)。返回回填了 id/sessionId/status/createTime 的座位列表")
    @PostMapping("/batch")
    public Result<List<Seat>> batchCreateSeats(@Valid @RequestBody BatchCreateSeatRequest req) {
        List<Seat> seats = req.getSeats();
        LocalDateTime now = LocalDateTime.now();
        seats.forEach(s -> {
            s.setSessionId(req.getSessionId());
            s.setStatus(0);
            s.setCreateTime(now);
        });
        seatMapper.batchInsert(seats);
        return Result.success(seats);
    }

    @Operation(summary = "场次座位列表")
    @GetMapping("/list")
    public Result<List<Seat>> listSeats(@Parameter(description = "场次 ID") @RequestParam Long sessionId) {
        return Result.success(seatMapper.selectBySessionId(sessionId));
    }

    @Operation(summary = "保存/覆盖场次价格区域", description = "覆盖式保存。areaId 字符串需与 seat.areaId 对应；通常用于覆盖场地模板复制过来的默认价格")
    @PostMapping("/area/save")
    public Result<Void> saveAreas(@Valid @RequestBody SaveAreasRequest req) {
        List<SeatArea> areas = req.getAreas();
        areas.forEach(a -> a.setSessionId(req.getSessionId()));
        seatAreaService.saveAreas(req.getSessionId(), areas);
        return Result.success();
    }

    @Operation(summary = "场次价格区域列表")
    @GetMapping("/area/list")
    public Result<List<SeatArea>> listAreas(@Parameter(description = "场次 ID") @RequestParam Long sessionId) {
        return Result.success(seatAreaService.getAreasBySession(sessionId));
    }

    @Operation(summary = "预热场次库存到 Redis",
            description = "把该场次所有可售座位 ID 写入 Redis Set、座位详情写 Hash、区域价格写 Hash。预热完成后还需调 /api/admin/session/{id}/publish 才正式开售")
    @PostMapping("/warmup/{sessionId}")
    public Result<Void> warmupSeats(@Parameter(description = "场次 ID") @PathVariable Long sessionId) {
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);
        if (seats == null || seats.isEmpty()) {
            return Result.fail(400, "该场次暂无座位数据");
        }
        List<SeatArea> areas = seatAreaService.getAreasBySession(sessionId);
        if (areas == null || areas.isEmpty()) {
            return Result.fail(400, "该场次暂无价格区域数据，请先调用 /area/save");
        }
        inventoryService.warmup(sessionId, seats, areas);
        return Result.success();
    }
}
