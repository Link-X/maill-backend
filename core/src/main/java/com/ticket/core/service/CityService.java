package com.ticket.core.service;

import com.ticket.core.domain.entity.City;
import com.ticket.core.mapper.CityMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 城市只读服务（数据由 schema.sql seed，不开放写入）
 */
@Service
public class CityService {

    private final CityMapper cityMapper;

    public CityService(CityMapper cityMapper) {
        this.cityMapper = cityMapper;
    }

    public List<City> listEnabled() {
        return cityMapper.selectEnabled();
    }

    public List<City> listByCondition(Integer status, String keyword) {
        return cityMapper.selectByCondition(status, keyword);
    }
}
