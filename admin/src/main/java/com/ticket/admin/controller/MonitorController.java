package com.ticket.admin.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "实时监控", description = "场次维度的实时座位/订单状态监控")
@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorController {

    private final SeatInventoryService inventoryService;
    private final SessionService sessionService;

    public MonitorController(SeatInventoryService inventoryService, SessionService sessionService) {
        this.inventoryService = inventoryService;
        this.sessionService = sessionService;
    }

    @Operation(summary = "场次座位实时统计", description = "返回该场次：总座位数、可售（从 Redis Set 实时读）、已售（= 总数 - 可售）。用于运营看板实时刷新")
    @GetMapping("/dashboard")
    public Result<?> getDashboard(
            @Parameter(description = "场次 ID", required = true) @RequestParam Long sessionId) {
        ShowSession session = sessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "场次不存在");
        }

        long availableCount = inventoryService.getAvailableCount(sessionId);
        return Result.success(Map.of(
                "sessionId",      sessionId,
                "totalSeats",     session.getTotalSeats(),
                "availableCount", availableCount,
                "soldCount",      session.getTotalSeats() - availableCount
        ));
    }
}
