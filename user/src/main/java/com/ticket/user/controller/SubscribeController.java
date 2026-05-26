package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.SubscribeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Tag(name = "订阅", description = "演出开售提醒订阅")
@RestController
@RequestMapping("/api/subscribe")
public class SubscribeController {

    private final SubscribeService service;

    public SubscribeController(SubscribeService service) {
        this.service = service;
    }

    @Operation(summary = "订阅开售提醒")
    @PostMapping("/add")
    public Result<Boolean> add(@Valid @RequestBody AddRequest req) {
        return Result.success(service.add(currentUserId(), req.showId, req.notifyBeforeMinutes));
    }

    @Operation(summary = "取消订阅")
    @PostMapping("/remove")
    public Result<Boolean> remove(@Valid @RequestBody RemoveRequest req) {
        return Result.success(service.remove(currentUserId(), req.showId));
    }

    @Operation(summary = "是否已订阅")
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam @NotNull Long showId) {
        return Result.success(service.isSubscribed(currentUserId(), showId));
    }

    @Operation(summary = "我的订阅列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.list(currentUserId(), page, size));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Data public static class AddRequest { @NotNull Long showId; Integer notifyBeforeMinutes; }
    @Data public static class RemoveRequest { @NotNull Long showId; }
}
