package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Article;
import com.ticket.core.service.ArticleService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "资讯", description = "用户端资讯列表/详情")
@NoLogin
@RestController
@RequestMapping("/api/article")
public class ArticleController {

    private final ArticleService service;

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @Operation(summary = "已发布资讯列表(可按分类过滤)")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.listPublished(categoryId, page, size));
    }

    @Operation(summary = "资讯详情(自动 view_count + 1)")
    @GetMapping("/{id}")
    public Result<Article> get(@PathVariable Long id) {
        return Result.success(service.getByIdAndIncrView(id));
    }

    @Operation(summary = "艺人相关资讯")
    @GetMapping("/by-artist/{artistId}")
    public Result<List<Article>> byArtist(
            @PathVariable Long artistId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(service.listByArtist(artistId, page, size));
    }
}
