package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.vo.ShowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 演出 Mapper 接口
 */
@Mapper
public interface ShowMapper {

    /**
     * 插入演出（自动生成主键）
     */
    int insert(Show show);

    /**
     * 更新演出信息
     */
    int update(Show show);

    /**
     * 根据 ID 查询演出
     */
    Show selectById(Long id);

    /**
     * 查询所有演出
     */
    List<Show> selectAll();

    /**
     * 根据状态查询演出列表
     */
    List<Show> selectByStatus(Integer status);

    /**
     * 带条件分页查询（name/categoryId/cityCode/venue 筛选；
     * name/venue 前缀模糊匹配，categoryId/cityCode 精确）
     * 返回 ShowVO（含 categoryName / cityName / address，LEFT JOIN category & city）
     */
    List<ShowVO> selectVOByCondition(@Param("name") String name,
                                     @Param("categoryId") Long categoryId,
                                     @Param("cityCode") String cityCode,
                                     @Param("venue") String venue,
                                     @Param("status") Integer status,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 带条件统计总数
     */
    int countByCondition(@Param("name") String name,
                         @Param("categoryId") Long categoryId,
                         @Param("cityCode") String cityCode,
                         @Param("venue") String venue,
                         @Param("status") Integer status);

    /** 单个演出 VO（含 categoryName） */
    ShowVO selectVOById(@Param("id") Long id);

    /**
     * 按 ID 列表批量查询演出（IN 查询，避免 N+1）
     */
    List<Show> selectByIds(@Param("ids") List<Long> ids);
}
