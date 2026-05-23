package com.ticket.core.mapper;

import com.ticket.core.domain.entity.City;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CityMapper {

    /** 用户端首页 tabs：仅 status=1，按 sort,id 排序 */
    List<City> selectEnabled();

    /** 管理端列表，可选 status / keyword(name 前缀) 筛选 */
    List<City> selectByCondition(@Param("status") Integer status,
                                 @Param("keyword") String keyword);

    City selectByCode(@Param("code") String code);

    /** 批量按 code 查询，用于订单/Show 列表填充 cityName */
    List<City> selectByCodes(@Param("codes") List<String> codes);
}
