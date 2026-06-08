package com.ticket.user.controller;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.vo.PageVO;
import com.ticket.core.domain.vo.SessionPurchaseLimitVO;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.service.PurchaseLimitService;
import com.ticket.core.service.SessionService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.SessionDetailRequest;
import com.ticket.user.dto.SessionListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 场次相关接口。
 *
 * <p>@NoLogin 按方法级别声明 — list / detail 公开,myLimit 需要登录态。
 */
@Tag(name = "场次(用户端)", description = "场次列表与座位图详情;座位图含实时可售状态(从 Redis 读)和演出/城市信息")
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;
    private final PurchaseLimitService purchaseLimitService;

    public SessionController(SessionService sessionService,
                             PurchaseLimitService purchaseLimitService) {
        this.sessionService = sessionService;
        this.purchaseLimitService = purchaseLimitService;
    }

    @NoLogin
    @Operation(summary = "演出下的场次分页列表",
            description = "支持 status / startTime / endTime 筛选;默认按 startTime 升序")
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

    @NoLogin
    @Operation(summary = "场次座位图详情",
            description = "返回 rowCount×colCount 网格、价格区域、每个座位实时可售状态(0=可售 1=已锁 2=已售,从 Redis 读取),并冗余演出/城市/地址信息(前端无需再调 /api/show/{id})")
    @SecurityRequirements({})
    @PostMapping("/detail")
    public Result<SessionSeatResponse> getSessionSeats(@Valid @RequestBody SessionDetailRequest req) {
        return Result.success(sessionService.getSeatSection(req.getSessionId()));
    }

    /**
     * 查询当前用户在该场次的限购状态(剩余可购张数)。
     * 前端在下单页面拉取后用于:
     *  - 选座区:超过 remaining 后禁止再点座位
     *  - 派座区:数量选择器上限 = min(remaining, MAX_QTY);情侣对取 remaining/2 对
     */
    @Operation(summary = "我的剩余可购张数(需登录)",
            description = "返回 { limitPerUser, purchased, remaining }。purchased 含未支付锁定 + 已支付。" +
                          "派座区情侣对上限 = remaining / 2(每对算 2 张)。")
    @GetMapping("/{sessionId}/myLimit")
    public Result<SessionPurchaseLimitVO> myLimit(
            @Parameter(description = "场次 ID") @PathVariable Long sessionId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ShowSession session = sessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        int limitPerUser = session.getLimitPerUser() != null ? session.getLimitPerUser() : 0;
        int purchased = purchaseLimitService.getPurchasedCount(sessionId, userId);
        int remaining = Math.max(0, limitPerUser - purchased);
        return Result.success(new SessionPurchaseLimitVO(limitPerUser, purchased, remaining));
    }
}
