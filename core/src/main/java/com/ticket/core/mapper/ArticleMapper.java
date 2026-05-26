package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleMapper {

    int insert(Article article);
    int update(Article article);
    int deleteById(@Param("id") Long id);

    /** 发布时一次性设置 status=1 + publishedAt + updateTime */
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("publishedAt") LocalDateTime publishedAt,
                     @Param("updateTime") LocalDateTime updateTime);

    int incrViewCount(@Param("id") Long id);

    /** 批量累加(由 Redis 缓冲区定时回写调用) */
    int incrViewCountBy(@Param("id") Long id, @Param("delta") long delta);

    Article selectById(@Param("id") Long id);

    /**
     * admin 列表查询。
     * @param status null=不过滤
     * @param categoryId null=不过滤
     * @param keyword null/空=不过滤;按 title 前缀模糊
     * @param offset 偏移
     * @param size 条数
     */
    List<Article> selectByCondition(@Param("status") Integer status,
                                    @Param("categoryId") Long categoryId,
                                    @Param("keyword") String keyword,
                                    @Param("offset") Integer offset,
                                    @Param("size") Integer size);

    int countByCondition(@Param("status") Integer status,
                         @Param("categoryId") Long categoryId,
                         @Param("keyword") String keyword);

    /** user 端:status=1,按 publishedAt DESC */
    List<Article> selectPublished(@Param("categoryId") Long categoryId,
                                  @Param("offset") Integer offset,
                                  @Param("size") Integer size);

    int countPublished(@Param("categoryId") Long categoryId);

    /** 艺人相关资讯(status=1) */
    List<Article> selectByArtistId(@Param("artistId") Long artistId,
                                   @Param("offset") Integer offset,
                                   @Param("size") Integer size);
}
