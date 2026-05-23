package com.ticket.admin.controller;

import com.ticket.admin.dto.SessionCreateRequest;
import com.ticket.admin.dto.SessionUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.ShowService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/session")
public class SessionController {

    private final ShowService showService;

    public SessionController(ShowService showService) {
        this.showService = showService;
    }

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
        // status 后端固定为 0；totalSeats / rowCount / colCount 由 service 从 Room 自动计算
        return Result.success(showService.createSession(session));
    }

    @PutMapping("/update")
    public Result<ShowSession> updateSession(@Valid @RequestBody SessionUpdateRequest req) {
        // 先读出现有记录，仅覆盖允许更新的字段，防止把后端管理的字段(status/totalSeats/rowCount/colCount/showId/roomId)清空
        ShowSession existing = showService.getSession(req.getId());
        if (existing == null) {
            return Result.fail(404, "场次不存在");
        }
        existing.setName(req.getName());
        existing.setStartTime(req.getStartTime());
        existing.setEndTime(req.getEndTime());
        existing.setLimitPerUser(req.getLimitPerUser());
        existing.setExtend(req.getExtend());
        return Result.success(showService.updateSession(existing));
    }

    @GetMapping("/{id}")
    public Result<ShowSession> getSession(@PathVariable Long id) {
        return Result.success(showService.getSession(id));
    }

    @GetMapping("/list")
    public Result<?> listSessions(@RequestParam Long showId) {
        return Result.success(showService.listSessions(showId));
    }

    /**
     * 发布场次开售（需先完成座位预热）
     */
    @PutMapping("/{sessionId}/publish")
    public Result<?> publishSession(@PathVariable Long sessionId) {
        showService.publishSession(sessionId);
        return Result.success("场次已发布开售");
    }
}
