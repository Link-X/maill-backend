package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.vo.PageVO;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.service.SessionService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.SessionDetailRequest;
import com.ticket.user.dto.SessionListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "场次（用户端）", description = "场次列表与座位图详情；座位图含实时可售状态（从 Redis 读）和演出/城市信息")
@NoLogin
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "演出下的场次分页列表", description = "支持 status / startTime / endTime 筛选；默认按 startTime 升序")
    @SecurityRequirements({})
    @PostMapping("/list")
    public Result<PageVO<ShowSession>> listSessions(@Valid @RequestBody SessionListRequest req) {
        List<ShowSession> list = sessionService.listPaged(
                req.getShowId(), req.getStatus(), req.getStartTime(), req.getEndTime(),
                req.getPage(), req.getSize());
        int total = sessionService.count(
                req.getShowId(), req.getStatus(), req.getStartTime(), req.getEndTime());
        return Result.success(PageVO.of(total, list));
    }

    @Operation(summary = "场次座位图详情", description = "返回 rowCount×colCount 网格、价格区域、每个座位实时可售状态（0=可售 1=已锁 2=已售，从 Redis 读取），并冗余演出/城市/地址信息（前端无需再调 /api/show/{id}）")
    @SecurityRequirements({})
    @PostMapping("/detail")
    public Result<SessionSeatResponse> getSessionSeats(@Valid @RequestBody SessionDetailRequest req) {
        return Result.success(sessionService.getSeatSection(req.getSessionId()));
    }
}
