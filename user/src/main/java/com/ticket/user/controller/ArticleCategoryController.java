package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ArticleCategory;
import com.ticket.core.service.ArticleCategoryService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "资讯分类", description = "用户端只读列表")
@NoLogin
@RestController
@RequestMapping("/api/article-category")
public class ArticleCategoryController {

    private final ArticleCategoryService service;

    public ArticleCategoryController(ArticleCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "启用的资讯分类列表")
    @GetMapping("/list")
    public Result<List<ArticleCategory>> list() {
        return Result.success(service.listEnabled());
    }
}
