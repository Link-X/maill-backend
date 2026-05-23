package com.ticket.admin.controller;

import com.ticket.admin.dto.CategoryCreateRequest;
import com.ticket.admin.dto.CategoryUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Category;
import com.ticket.core.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 演出分类管理（管理端）
 */
@RestController
@RequestMapping("/api/admin/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) String keyword) {
        return Result.success(categoryService.listByCondition(status, keyword));
    }

    @PostMapping("/create")
    public Result<Category> create(@Valid @RequestBody CategoryCreateRequest req) {
        Category category = new Category();
        category.setName(req.getName());
        category.setSort(req.getSort());
        category.setIcon(req.getIcon());
        category.setStatus(req.getStatus());
        return Result.success(categoryService.create(category));
    }

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

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success(null);
    }
}
