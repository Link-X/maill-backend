package com.ticket.admin.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorController {

    private final SeatInventoryService inventoryService;
    private final SessionService sessionService;

    public MonitorController(SeatInventoryService inventoryService, SessionService sessionService) {
        this.inventoryService = inventoryService;
        this.sessionService = sessionService;
    }

    @GetMapping("/dashboard")
    public Result<?> getDashboard(@RequestParam Long sessionId) {
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
