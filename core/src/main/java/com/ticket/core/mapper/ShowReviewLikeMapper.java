package com.ticket.core.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShowReviewLikeMapper {
    int insertIgnore(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
    int delete(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
    int countLike(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
    /** 给定 reviewIds 返回当前用户已点赞的子集 */
    List<Long> selectLikedReviewIds(@Param("userId") Long userId, @Param("reviewIds") List<Long> reviewIds);
}
