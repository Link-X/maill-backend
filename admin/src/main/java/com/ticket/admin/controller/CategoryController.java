package com.ticket.admin.controller;

import com.ticket.admin.dto.CategoryCreateRequest;
import com.ticket.admin.dto.CategoryUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Category;
import com.ticket.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "演出分类管理", description = "维护演出分类（演唱会/相声/脱口秀/体育...）。演出通过 categoryId 关联本表；删除被引用的分类返回业务错误 1012")
@RestController
@RequestMapping("/api/admin/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类列表", description = "管理端分类列表；可按状态和 name 前缀筛选。按 sort 升序、id 升序返回")
    @GetMapping("/list")
    public Result<List<Category>> list(
            @Parameter(description = "状态过滤：0=禁用 1=启用；不传则返回全部") @RequestParam(required = false) Integer status,
            @Parameter(description = "分类名前缀模糊匹配，例如传 '演' 可匹配 '演唱会'") @RequestParam(required = false) String keyword) {
        return Result.success(categoryService.listByCondition(status, keyword));
    }

    @Operation(summary = "新建分类", description = "name 全表唯一，重名返回业务错误 1013 CATEGORY_NAME_DUPLICATED")
    @PostMapping("/create")
    public Result<Category> create(@Valid @RequestBody CategoryCreateRequest req) {
        Category category = new Category();
        category.setName(req.getName());
        category.setSort(req.getSort());
        category.setIcon(req.getIcon());
        category.setStatus(req.getStatus());
        return Result.success(categoryService.create(category));
    }

    @Operation(summary = "更新分类", description = "改名时校验重名（排除自身）。把 status 改为 0 即'禁用'，用户端 /api/category/list 将不再返回此项")
    @PutMapping("/update")
    public Result<Category> update(@Valid @RequestBody CategoryUpdateRequest req) {
        Category category = new Category();
        category.setId(req.getId());
        category.setName(req.getName());
        category.setSort(req.getSort());
        category.setIcon(req.getIcon());
        category.setStatus(req.getStatus());
        return Result.success(categoryService.update(category));
    }

    @Operation(summary = "删除分类", description = "若该分类被任意演出引用，返回业务错误 1012 CATEGORY_IN_USE（msg 中会附带被引用数量）。无引用则直接物理删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "分类 ID") @PathVariable Long id) {
        categoryService.delete(id);
        return Result.success(null);
    }
}
