package com.ticket.admin.controller;

import com.ticket.admin.dto.SessionCreateRequest;
import com.ticket.admin.dto.SessionUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.service.SessionService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
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
        return Result.success(sessionService.create(session));
    }

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

    @GetMapping("/{id}")
    public Result<ShowSession> getSession(@PathVariable Long id) {
        return Result.success(sessionService.getById(id));
    }

    @GetMapping("/list")
    public Result<?> listSessions(@RequestParam Long showId) {
        return Result.success(sessionService.listByShowId(showId));
    }

    /**
     * 发布场次开售（需先完成座位预热）
     */
    @PutMapping("/{sessionId}/publish")
    public Result<?> publishSession(@PathVariable Long sessionId) {
        sessionService.publish(sessionId);
        return Result.success("场次已发布开售");
    }
}
