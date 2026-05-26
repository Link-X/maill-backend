package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Artist;
import com.ticket.core.service.ArtistService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "艺人", description = "用户端艺人:列表/主页/关注/我的关注")
@RestController
@RequestMapping("/api/artist")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @Operation(summary = "艺人列表(已上架)")
    @NoLogin
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<Artist> list = artistService.listEnabled(page, size);
        int total = artistService.countEnabled();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @Operation(summary = "艺人详情")
    @NoLogin
    @GetMapping("/{id}")
    public Result<Artist> get(@PathVariable Long id) {
        return Result.success(artistService.getById(id));
    }

    @Operation(summary = "关注艺人")
    @PostMapping("/follow")
    public Result<Boolean> follow(@RequestBody FollowRequest req) {
        Long userId = currentUserId();
        return Result.success(artistService.follow(userId, req.artistId));
    }

    @Operation(summary = "取消关注")
    @PostMapping("/unfollow")
    public Result<Boolean> unfollow(@RequestBody FollowRequest req) {
        Long userId = currentUserId();
        return Result.success(artistService.unfollow(userId, req.artistId));
    }

    @Operation(summary = "是否已关注")
    @GetMapping("/follow/check")
    public Result<Boolean> check(@RequestParam @NotNull Long artistId) {
        Long userId = currentUserId();
        return Result.success(artistService.isFollowing(userId, artistId));
    }

    @Operation(summary = "我关注的艺人")
    @GetMapping("/follow/list")
    public Result<List<Artist>> followList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = currentUserId();
        return Result.success(artistService.listFollowedArtists(userId, page, size));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @lombok.Data
    public static class FollowRequest {
        @NotNull public Long artistId;
    }
}
