package com.ticket.core.mapper;

import com.ticket.core.domain.entity.ArticleCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleCategoryMapper {

    int insert(ArticleCategory category);
    int update(ArticleCategory category);
    int deleteById(@Param("id") Long id);

    ArticleCategory selectById(@Param("id") Long id);
    ArticleCategory selectByName(@Param("name") String name);

    /** admin 列表;status null=不过滤 */
    List<ArticleCategory> selectByCondition(@Param("status") Integer status);

    /** user 端启用列表 */
    List<ArticleCategory> selectEnabled();

    /** 统计该分类下的资讯数(删除前校验) */
    int countArticlesByCategoryId(@Param("id") Long id);
}
