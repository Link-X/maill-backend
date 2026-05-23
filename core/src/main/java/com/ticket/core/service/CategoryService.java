package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Category;
import com.ticket.core.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演出分类服务
 */
@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Transactional
    public Category create(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名不能为空");
        }
        category.setName(category.getName().trim());
        if (categoryMapper.selectByName(category.getName()) != null) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (category.getSort() == null) category.setSort(0);
        if (category.getStatus() == null) category.setStatus(1);
        category.setCreateTime(now);
        category.setUpdateTime(now);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public Category update(Category category) {
        if (category.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类 ID 不能为空");
        }
        Category exist = categoryMapper.selectById(category.getId());
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        // 改名时校验重名（排除自己）
        if (category.getName() != null) {
            category.setName(category.getName().trim());
            Category byName = categoryMapper.selectByName(category.getName());
            if (byName != null && !byName.getId().equals(category.getId())) {
                throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED);
            }
        }
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.update(category);
        return categoryMapper.selectById(category.getId());
    }

    /**
     * 删除前校验是否被 show 引用，被引用则报 CATEGORY_IN_USE。
     */
    @Transactional
    public void delete(Long id) {
        int refCount = categoryMapper.countShowsByCategoryId(id);
        if (refCount > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE,
                    "分类被 " + refCount + " 个演出引用，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    public List<Category> listByCondition(Integer status, String keyword) {
        return categoryMapper.selectByCondition(status, keyword);
    }

    /** 用户端列表：仅启用的，按 sort 排序 */
    public List<Category> listEnabled() {
        return categoryMapper.selectEnabled();
    }
}
