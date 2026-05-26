package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BannerMapper {

    int insert(Banner banner);

    int update(Banner banner);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("updateTime") LocalDateTime updateTime);

    int updateSort(@Param("id") Long id,
                   @Param("sort") Integer sort,
                   @Param("updateTime") LocalDateTime updateTime);

    int deleteById(@Param("id") Long id);

    Banner selectById(@Param("id") Long id);

    /**
     * admin 列表查询。
     * @param status null = 不过滤
     */
    List<Banner> selectByCondition(@Param("status") Integer status);

    /**
     * user 端有效 banner:status=1 且当前时间在 start_at/end_at 范围内（任一字段 NULL 视为不限）。
     * 按 sort ASC, id ASC 排序。
     */
    List<Banner> selectEffective(@Param("now") LocalDateTime now);
}
