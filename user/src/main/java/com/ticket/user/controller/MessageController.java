package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Tag(name = "消息中心", description = "用户消息列表 / 已读 / 删除 / 未读数")
@RestController
@RequestMapping("/api/message")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "消息列表(分页)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = currentUserId();
        return Result.success(service.listForUser(userId, type, page, size));
    }

    @Operation(summary = "未读数(各类型 + 总数)")
    @GetMapping("/unread/count")
    public Result<Map<String, Integer>> unread() {
        Long userId = currentUserId();
        return Result.success(service.unreadCounts(userId));
    }

    @Operation(summary = "标记已读(传 user_message 主键 id 列表)")
    @PostMapping("/read")
    public Result<Integer> read(@RequestBody IdListRequest req) {
        Long userId = currentUserId();
        return Result.success(service.markRead(userId, req.getIds()));
    }

    @Operation(summary = "全部已读(type 可选)")
    @PostMapping("/read/all")
    public Result<Integer> readAll(@RequestBody(required = false) ReadAllRequest req) {
        Long userId = currentUserId();
        Integer type = req == null ? null : req.getType();
        return Result.success(service.markAllRead(userId, type));
    }

    @Operation(summary = "删除消息")
    @PostMapping("/delete")
    public Result<Integer> delete(@RequestBody IdListRequest req) {
        Long userId = currentUserId();
        return Result.success(service.delete(userId, req.getIds()));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Data
    public static class IdListRequest {
        @NotEmpty
        private List<Long> ids;
    }

    @Data
    public static class ReadAllRequest {
        private Integer type;
    }
}
