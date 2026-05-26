package com.ticket.core.mapper;
import com.ticket.core.domain.entity.ShowReviewReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShowReviewReportMapper {
    int insert(ShowReviewReport r);
    ShowReviewReport selectById(@Param("id") Long id);
    List<ShowReviewReport> selectByStatus(@Param("status") Integer status,
                                          @Param("offset") Integer offset, @Param("size") Integer size);
    int countByStatus(@Param("status") Integer status);
    int updateHandle(@Param("id") Long id, @Param("status") Integer status,
                     @Param("handlerId") Long handlerId, @Param("handledAt") LocalDateTime handledAt);
}
