package com.ticket.core.mapper;

import com.ticket.core.domain.entity.SeatArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 座位区域 Mapper 接口
 */
@Mapper
public interface SeatAreaMapper {

    /**
     * 批量插入座位区域
     */
    int batchInsert(@Param("areas") List<SeatArea> areas);

    /**
     * 根据场次 ID 查询座位区域列表
     */
    List<SeatArea> selectBySessionId(Long sessionId);

    /**
     * 根据场次 ID 删除座位区域
     */
    int deleteBySessionId(Long sessionId);

    /**
     * 按 session + areaId 查询单个区域,用于派座流程校验 sale_mode/库存/价格
     */
    SeatArea selectBySessionAndArea(@Param("sessionId") Long sessionId,
                                    @Param("areaId") String areaId);

    /**
     * 更新区域售卖模式与派座策略(admin 编辑用)
     */
    int updateSaleConfig(@Param("sessionId") Long sessionId,
                         @Param("areaId") String areaId,
                         @Param("saleMode") Integer saleMode,
                         @Param("allocateStrategy") Integer allocateStrategy);

    /**
     * 更新区域统计数(预热时按实际座位重算 single/couple)
     */
    int updateTotals(@Param("sessionId") Long sessionId,
                     @Param("areaId") String areaId,
                     @Param("singleTotal") Integer singleTotal,
                     @Param("coupleTotal") Integer coupleTotal);
}
