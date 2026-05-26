package com.ticket.admin.controller;

import com.ticket.admin.dto.SessionCreateRequest;
import com.ticket.admin.dto.SessionUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "场次管理", description = "演出场次 CRUD。status 不在此修改,由定时任务在 openSaleTime/endTime 自动流转。传 roomId 后端会自动从场地模板复制座位和价格区域到该场次")
@RestController
@RequestMapping("/api/admin/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "创建场次", description = "传 roomId 时:后端从 Room 复制 rowCount/colCount 写入 show_session,并把座位模板和默认价格区域复制到该场次(用座位数回填 total_seats);不传 roomId 则需另调 /seat/batch 与 /seat/area/save。status 强制为 0,由定时任务在 openSaleTime 自动 warmup + 流转为销售中")
    @PostMapping("/create")
    public Result<ShowSession> createSession(@Valid @RequestBody SessionCreateRequest req) {
        ShowSession session = new ShowSession();
        session.setShowId(req.getShowId());
        session.setRoomId(req.getRoomId());
        session.setName(req.getName());
        session.setStartTime(req.getStartTime());
        session.setEndTime(req.getEndTime());
        session.setLimitPerUser(req.getLimitPerUser());
        session.setOpenSaleTime(req.getOpenSaleTime());
        session.setExtend(req.getExtend());
        return Result.success(sessionService.create(session));
    }

    @Operation(summary = "更新场次", description = "先读出原记录,仅覆盖允许更新的字段(name/startTime/endTime/limitPerUser/openSaleTime/extend),防止前端漏传字段把后端管理的列清空。status 不允许在此修改,由定时任务管理")
    @PutMapping("/update")
    public Result<ShowSession> updateSession(@Valid @RequestBody SessionUpdateRequest req) {
        ShowSession existing = sessionService.getById(req.getId());
        if (existing == null) {
            return Result.fail(404, "场次不存在");
        }
        existing.setName(req.getName());
        existing.setStartTime(req.getStartTime());
        existing.setEndTime(req.getEndTime());
        existing.setLimitPerUser(req.getLimitPerUser());
        existing.setOpenSaleTime(req.getOpenSaleTime());
        existing.setExtend(req.getExtend());
        return Result.success(sessionService.update(existing));
    }

    @Operation(summary = "场次详情")
    @GetMapping("/{id}")
    public Result<ShowSession> getSession(@Parameter(description = "场次 ID") @PathVariable Long id) {
        return Result.success(sessionService.getById(id));
    }

    @Operation(summary = "演出下的场次列表")
    @GetMapping("/list")
    public Result<List<ShowSession>> listSessions(@Parameter(description = "演出 ID") @RequestParam Long showId) {
        return Result.success(sessionService.listByShowId(showId));
    }
}
