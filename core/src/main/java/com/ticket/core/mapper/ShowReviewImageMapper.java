package com.ticket.core.mapper;
import com.ticket.core.domain.entity.ShowReviewImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ShowReviewImageMapper {
    int batchInsert(@Param("list") List<ShowReviewImage> list);
    int deleteByReviewId(@Param("reviewId") Long reviewId);
    List<ShowReviewImage> selectByReviewId(@Param("reviewId") Long reviewId);
    List<ShowReviewImage> selectByReviewIds(@Param("reviewIds") List<Long> reviewIds);
}
