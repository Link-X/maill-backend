package com.ticket.admin.controller;

import com.ticket.admin.dto.ArticleCategoryCreateRequest;
import com.ticket.admin.dto.ArticleCategoryUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.ArticleCategory;
import com.ticket.core.service.ArticleCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "资讯分类管理", description = "维护资讯分类(独立于演出分类)")
@RestController
@RequestMapping("/api/admin/article-category")
public class ArticleCategoryController {

    private final ArticleCategoryService service;

    public ArticleCategoryController(ArticleCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "列表")
    @GetMapping("/list")
    public Result<List<ArticleCategory>> list(
            @Parameter(description = "0/1;不传则全部") @RequestParam(required = false) Integer status) {
        return Result.success(service.listByCondition(status));
    }

    @Operation(summary = "新建")
    @PostMapping("/create")
    public Result<ArticleCategory> create(@Valid @RequestBody ArticleCategoryCreateRequest req) {
        ArticleCategory cat = new ArticleCategory();
        cat.setName(req.getName());
        cat.setSort(req.getSort());
        cat.setStatus(req.getStatus());
        return Result.success(service.create(cat));
    }

    @Operation(summary = "更新")
    @PutMapping("/update")
    public Result<ArticleCategory> update(@Valid @RequestBody ArticleCategoryUpdateRequest req) {
        ArticleCategory cat = new ArticleCategory();
        cat.setId(req.getId());
        cat.setName(req.getName());
        cat.setSort(req.getSort());
        cat.setStatus(req.getStatus());
        return Result.success(service.update(cat));
    }

    @Operation(summary = "删除(被引用时返回 1042)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(null);
    }
}
