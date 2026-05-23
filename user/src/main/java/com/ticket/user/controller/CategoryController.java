package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Category;
import com.ticket.core.service.CategoryService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "演出分类（用户端）", description = "用户端首页 tabs 数据源")
@NoLogin
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "启用的分类列表", description = "仅返回 status=1，按 sort 升序")
    @SecurityRequirements({})
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.listEnabled());
    }
}
