package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.FavoriteGroup;
import com.ticket.core.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Tag(name = "收藏", description = "演出收藏 + 自定义分组")
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @Operation(summary = "添加收藏")
    @PostMapping("/add")
    public Result<Boolean> add(@Valid @RequestBody AddRequest req) {
        return Result.success(service.add(currentUserId(), req.showId, req.groupId));
    }

    @Operation(summary = "取消收藏")
    @PostMapping("/remove")
    public Result<Boolean> remove(@Valid @RequestBody RemoveRequest req) {
        return Result.success(service.remove(currentUserId(), req.showId));
    }

    @Operation(summary = "移动到分组")
    @PostMapping("/move")
    public Result<Void> move(@Valid @RequestBody MoveRequest req) {
        service.move(currentUserId(), req.showId, req.groupId);
        return Result.success(null);
    }

    @Operation(summary = "是否已收藏")
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam @NotNull Long showId) {
        return Result.success(service.isFavorited(currentUserId(), showId));
    }

    @Operation(summary = "我的收藏列表(可按 groupId 过滤,或 unset=true 仅看未分组)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean unset,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.list(currentUserId(), groupId, unset, page, size));
    }

    // ----- 分组 -----

    @Operation(summary = "分组列表")
    @GetMapping("/group/list")
    public Result<List<FavoriteGroup>> groupList() {
        return Result.success(service.listGroups(currentUserId()));
    }

    @Operation(summary = "新建分组")
    @PostMapping("/group/create")
    public Result<FavoriteGroup> createGroup(@Valid @RequestBody NameRequest req) {
        return Result.success(service.createGroup(currentUserId(), req.name));
    }

    @Operation(summary = "重命名分组")
    @PostMapping("/group/rename")
    public Result<FavoriteGroup> renameGroup(@Valid @RequestBody RenameRequest req) {
        return Result.success(service.renameGroup(currentUserId(), req.id, req.name));
    }

    @Operation(summary = "删除分组(被引用的收藏 group_id 置 NULL)")
    @PostMapping("/group/delete")
    public Result<Void> deleteGroup(@Valid @RequestBody IdRequest req) {
        service.deleteGroup(currentUserId(), req.id);
        return Result.success(null);
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Data public static class AddRequest { @NotNull Long showId; Long groupId; }
    @Data public static class RemoveRequest { @NotNull Long showId; }
    @Data public static class MoveRequest { @NotNull Long showId; Long groupId; }
    @Data public static class NameRequest { @NotBlank String name; }
    @Data public static class RenameRequest { @NotNull Long id; @NotBlank String name; }
    @Data public static class IdRequest { @NotNull Long id; }
}
