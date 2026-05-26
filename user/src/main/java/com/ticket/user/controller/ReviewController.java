package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ShowReview;
import com.ticket.core.domain.entity.ShowReviewReport;
import com.ticket.core.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

@Tag(name = "评价", description = "演出评价、楼中楼回复、点赞、举报")
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @Operation(summary = "发布一级评价")
    @PostMapping("/publish")
    public Result<ShowReview> publish(@Valid @RequestBody PublishRequest req) {
        Long userId = currentUserId();
        return Result.success(service.publish(userId, req.showId, req.orderId, req.rating, req.content, req.images));
    }

    @Operation(summary = "发布二级回复")
    @PostMapping("/reply")
    public Result<ShowReview> reply(@Valid @RequestBody ReplyRequest req) {
        Long userId = currentUserId();
        return Result.success(service.reply(userId, req.parentId, req.replyToUserId, req.content, req.images));
    }

    @Operation(summary = "一级评价列表(sort=latest|hottest)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam @NotNull Long showId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = currentUserIdOrNull();
        return Result.success(service.listFirstLevel(showId, sort, userId, page, size));
    }

    @Operation(summary = "二级回复列表")
    @GetMapping("/replies")
    public Result<Map<String, Object>> replies(
            @RequestParam @NotNull Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = currentUserIdOrNull();
        return Result.success(service.listReplies(parentId, userId, page, size));
    }

    @Operation(summary = "评价详情")
    @GetMapping("/detail/{id}")
    public Result<ShowReview> detail(@PathVariable Long id) {
        Long userId = currentUserIdOrNull();
        return Result.success(service.getDetail(id, userId));
    }

    @Operation(summary = "点赞")
    @PostMapping("/like")
    public Result<Boolean> like(@Valid @RequestBody IdRequest req) {
        return Result.success(service.like(currentUserId(), req.reviewId));
    }

    @Operation(summary = "取消点赞")
    @PostMapping("/unlike")
    public Result<Boolean> unlike(@Valid @RequestBody IdRequest req) {
        return Result.success(service.unlike(currentUserId(), req.reviewId));
    }

    @Operation(summary = "举报")
    @PostMapping("/report")
    public Result<ShowReviewReport> report(@Valid @RequestBody ReportRequest req) {
        return Result.success(service.report(currentUserId(), req.reviewId, req.reason));
    }

    @Operation(summary = "用户自删评价")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody IdRequest req) {
        service.deleteByUser(currentUserId(), req.reviewId);
        return Result.success(null);
    }

    @Operation(summary = "我发布的评价")
    @GetMapping("/my")
    public Result<Map<String, Object>> my(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.listByUser(currentUserId(), page, size));
    }

    @Operation(summary = "当前用户能否评价(前端按钮显隐用)")
    @GetMapping("/check-permission")
    public Result<Boolean> checkPermission(
            @RequestParam @NotNull Long showId,
            @RequestParam(required = false) Long orderId) {
        return Result.success(service.canReview(currentUserId(), showId, orderId));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Long currentUserIdOrNull() {
        try {
            Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return p instanceof Long ? (Long) p : null;
        } catch (Exception e) { return null; }
    }

    @Data public static class PublishRequest {
        @NotNull Long showId;
        Long orderId;
        @NotNull @Min(1) @Max(5) Integer rating;
        @NotBlank @Size(max = 2000) String content;
        @Size(max = 9) List<String> images;
    }
    @Data public static class ReplyRequest {
        @NotNull Long parentId;
        Long replyToUserId;
        @NotBlank @Size(max = 2000) String content;
        @Size(max = 9) List<String> images;
    }
    @Data public static class IdRequest { @NotNull Long reviewId; }
    @Data public static class ReportRequest {
        @NotNull Long reviewId;
        @NotBlank @Size(max = 200) String reason;
    }
}
