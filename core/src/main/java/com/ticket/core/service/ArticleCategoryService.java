package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.ArticleCategory;
import com.ticket.core.mapper.ArticleCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArticleCategoryService {

    private final ArticleCategoryMapper mapper;

    public ArticleCategoryService(ArticleCategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ArticleCategory create(ArticleCategory cat) {
        if (cat.getName() == null || cat.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分类名不能为空");
        }
        cat.setName(cat.getName().trim());
        if (mapper.selectByName(cat.getName()) != null) {
            throw new BusinessException(ErrorCode.ARTICLE_CATEGORY_NAME_DUPLICATED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (cat.getSort() == null) cat.setSort(0);
        if (cat.getStatus() == null) cat.setStatus(1);
        cat.setCreateTime(now);
        cat.setUpdateTime(now);
        mapper.insert(cat);
        return cat;
    }

    @Transactional
    public ArticleCategory update(ArticleCategory cat) {
        if (cat.getId() == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "分类 ID 不能为空");
        ArticleCategory exist = mapper.selectById(cat.getId());
        if (exist == null) throw new BusinessException(ErrorCode.ARTICLE_CATEGORY_NOT_FOUND);
        if (cat.getName() != null) {
            cat.setName(cat.getName().trim());
            ArticleCategory byName = mapper.selectByName(cat.getName());
            if (byName != null && !byName.getId().equals(cat.getId())) {
                throw new BusinessException(ErrorCode.ARTICLE_CATEGORY_NAME_DUPLICATED);
            }
        }
        cat.setUpdateTime(LocalDateTime.now());
        mapper.update(cat);
        return mapper.selectById(cat.getId());
    }

    @Transactional
    public void delete(Long id) {
        int ref = mapper.countArticlesByCategoryId(id);
        if (ref > 0) {
            throw new BusinessException(ErrorCode.ARTICLE_CATEGORY_IN_USE,
                    "分类被 " + ref + " 条资讯引用");
        }
        mapper.deleteById(id);
    }

    public ArticleCategory getById(Long id) {
        return mapper.selectById(id);
    }

    public List<ArticleCategory> listByCondition(Integer status) {
        return mapper.selectByCondition(status);
    }

    public List<ArticleCategory> listEnabled() {
        return mapper.selectEnabled();
    }
}
