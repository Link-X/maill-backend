package com.ticket.admin.controller;

import com.ticket.admin.dto.BatchCreateSeatRequest;
import com.ticket.admin.dto.SaveAreasRequest;
import com.ticket.admin.service.AdminSeatService;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.service.SeatAreaService;
import com.ticket.core.service.SeatInventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "座位（场次维度）", description = "不走场地模板(手动模式)时使用:直接给特定 sessionId 批量建座位、设价格。开售由定时任务自动 warmup,/warmup 接口仅作为改价/改座位后的应急覆盖")
@RestController
@RequestMapping("/api/admin/seat")
public class SeatController {

    private final AdminSeatService adminSeatService;
    private final SeatInventoryService inventoryService;
    private final SeatAreaService seatAreaService;

    public SeatController(AdminSeatService adminSeatService,
                          SeatInventoryService inventoryService,
                          SeatAreaService seatAreaService) {
        this.adminSeatService = adminSeatService;
        this.inventoryService = inventoryService;
        this.seatAreaService = seatAreaService;
    }

    @Operation(summary = "批量创建场次座位（手动模式）", description = "不走场地模板时使用。seats 中每个元素需提供: rowNo, colNo, type, areaId, seatName, pairSeatId(情侣座才填)。返回回填了 id/sessionId/status/createTime 的座位列表")
    @PostMapping("/batch")
    public Result<List<Seat>> batchCreateSeats(@Valid @RequestBody BatchCreateSeatRequest req) {
        return Result.success(adminSeatService.batchCreate(req.getSessionId(), req.getSeats()));
    }

    @Operation(summary = "场次座位列表")
    @GetMapping("/list")
    public Result<List<Seat>> listSeats(@Parameter(description = "场次 ID") @RequestParam Long sessionId) {
        return Result.success(adminSeatService.listBySession(sessionId));
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

    @Operation(summary = "更新区域售卖模式与派座策略",
            description = "saleMode: 1=用户选座, 2=系统派座;allocateStrategy: 1=连坐优先, 2=分散, 3=任意。" +
                          "销售中场次会自动触发 warmup 重建池子。同一场次内不同区域可独立设置(混合模式)。")
    @PostMapping("/area/saleConfig")
    public Result<Void> updateSaleConfig(
            @Parameter(description = "场次 ID") @RequestParam Long sessionId,
            @Parameter(description = "区域 ID") @RequestParam String areaId,
            @Parameter(description = "售卖模式 1=选座 2=派座") @RequestParam Integer saleMode,
            @Parameter(description = "派座策略 1=连坐优先 2=分散 3=任意,默认 1") @RequestParam(required = false) Integer allocateStrategy) {
        seatAreaService.updateSaleConfig(sessionId, areaId, saleMode, allocateStrategy);
        return Result.success();
    }

    @Operation(summary = "预热场次库存到 Redis(应急接口)",
            description = "正常流程由定时任务在 openSaleTime 自动预热,无需手动调用。仅当管理员修改了价格/座位后需要立即覆盖 Redis 数据时使用。把该场次所有可售座位 ID 写入 Redis Set、区域价格写 Hash")
    @PostMapping("/warmup/{sessionId}")
    public Result<Void> warmupSeats(@Parameter(description = "场次 ID") @PathVariable Long sessionId) {
        List<Seat> seats = adminSeatService.listBySession(sessionId);
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
