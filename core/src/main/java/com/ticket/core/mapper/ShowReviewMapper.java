package com.ticket.core.mapper;
import com.ticket.core.domain.entity.ShowReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShowReviewMapper {
    int insert(ShowReview r);
    int update(ShowReview r);
    int deleteById(@Param("id") Long id);

    ShowReview selectById(@Param("id") Long id);

    /** 一级评论(parent_id IS NULL)分页查询;sort 可为 'latest'/'hottest' */
    List<ShowReview> selectFirstLevel(@Param("showId") Long showId,
                                      @Param("sort") String sort,
                                      @Param("includeHidden") Boolean includeHidden,
                                      @Param("offset") Integer offset,
                                      @Param("size") Integer size);

    int countFirstLevel(@Param("showId") Long showId,
                        @Param("includeHidden") Boolean includeHidden);

    /** 二级回复 */
    List<ShowReview> selectReplies(@Param("parentId") Long parentId,
                                   @Param("offset") Integer offset,
                                   @Param("size") Integer size);

    int countReplies(@Param("parentId") Long parentId);

    /** 用户已发评价 */
    List<ShowReview> selectByUser(@Param("userId") Long userId,
                                  @Param("offset") Integer offset,
                                  @Param("size") Integer size);

    int incrLikeCount(@Param("id") Long id);
    int decrLikeCount(@Param("id") Long id);
    int incrReplyCount(@Param("id") Long id);
    int decrReplyCount(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime);

    /** 演出维度统计:平均评分 + 评价数(仅 status=0 且 parent_id IS NULL,仅 rating 非空) */
    java.util.Map<String, Object> aggregateRating(@Param("showId") Long showId);

    /** 演出维度评价总数(包含子回复也许?简化:只统计一级) */
    int countByShow(@Param("showId") Long showId);

    /** admin 列表 */
    List<ShowReview> selectAdminList(@Param("showId") Long showId,
                                     @Param("status") Integer status,
                                     @Param("keyword") String keyword,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    int countAdminList(@Param("showId") Long showId,
                       @Param("status") Integer status,
                       @Param("keyword") String keyword);
}
