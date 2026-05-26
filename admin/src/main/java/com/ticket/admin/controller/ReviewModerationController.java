package com.ticket.admin.controller;

import com.ticket.admin.dto.ReportHandleRequest;
import com.ticket.admin.dto.ReviewListRequest;
import com.ticket.common.result.Result;
import com.ticket.core.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Tag(name = "评价审核", description = "评价审核 + 举报处理")
@RestController
@RequestMapping("/api/admin/review")
public class ReviewModerationController {

    private final ReviewService service;

    public ReviewModerationController(ReviewService service) {
        this.service = service;
    }

    @Operation(summary = "评价列表")
    @PostMapping("/list")
    public Result<Map<String, Object>> list(@Valid @RequestBody ReviewListRequest req) {
        return Result.success(service.adminList(req.getShowId(), req.getStatus(), req.getKeyword(),
                req.getPage(), req.getSize()));
    }

    @Operation(summary = "隐藏")
    @PostMapping("/hide")
    public Result<Void> hide(@Valid @RequestBody IdReq req) { service.adminHide(req.reviewId); return Result.success(null); }

    @Operation(summary = "恢复")
    @PostMapping("/restore")
    public Result<Void> restore(@Valid @RequestBody IdReq req) { service.adminRestore(req.reviewId); return Result.success(null); }

    @Operation(summary = "硬删除")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody IdReq req) { service.adminDelete(req.reviewId); return Result.success(null); }

    @Operation(summary = "举报列表(status 可选)")
    @GetMapping("/report/list")
    public Result<Map<String, Object>> reportList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.adminReportList(status, page, size));
    }

    @Operation(summary = "处理举报(action: keep|delete)")
    @PostMapping("/report/handle")
    public Result<Void> handle(@Valid @RequestBody ReportHandleRequest req) {
        Long adminId = currentUserId();
        service.adminHandleReport(adminId, req.getReportId(), req.getAction());
        return Result.success(null);
    }

    private Long currentUserId() {
        try {
            Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return p instanceof Long ? (Long) p : null;
        } catch (Exception e) { return null; }
    }

    @Data
    public static class IdReq { @NotNull public Long reviewId; }
}
