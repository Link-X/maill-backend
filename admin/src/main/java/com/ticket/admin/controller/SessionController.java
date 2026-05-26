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

@Tag(name = "场次管理", description = "演出场次 CRUD + 发布开售。传 roomId 后端会自动从场地模板复制座位和价格区域到该场次")
@RestController
@RequestMapping("/api/admin/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "创建场次", description = "传 roomId 时：后端从 Room 复制 rowCount/colCount 写入 show_session，并把座位模板和默认价格区域复制到该场次（用座位数回填 total_seats）；不传 roomId 则需另调 /seat/batch 与 /seat/area/save。status 强制为 0，开售用 /publish")
    @PostMapping("/create")
    public Result<ShowSession> createSession(@Valid @RequestBody SessionCreateRequest req) {
        ShowSession session = new ShowSession();
        session.setShowId(req.getShowId());
        session.setRoomId(req.getRoomId());
        session.setName(req.getName());
        session.setStartTime(req.getStartTime());
        session.setEndTime(req.getEndTime());
        session.setLimitPerUser(req.getLimitPerUser());
        session.setExtend(req.getExtend());
        return Result.success(sessionService.create(session));
    }

    @Operation(summary = "更新场次", description = "先读出原记录，仅覆盖允许更新的字段（name/startTime/endTime/limitPerUser/extend），防止前端漏传字段把后端管理的列清空。改 status 请用 /publish 接口")
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

    @Operation(summary = "发布场次开售", description = "把 status 从 0 改为 1。建议先完成 /seat/warmup 把库存预热到 Redis 再发布，避免开售后用户立刻打到 DB 兜底逻辑")
    @PutMapping("/{sessionId}/publish")
    public Result<Void> publishSession(@Parameter(description = "场次 ID") @PathVariable Long sessionId) {
        sessionService.publish(sessionId);
        return Result.success();
    }
}
