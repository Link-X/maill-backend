package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.City;
import com.ticket.core.service.CityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 城市列表（管理端只读，用于后台创建演出时的下拉选）
 */
@RestController
@RequestMapping("/api/admin/city")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping("/list")
    public Result<List<City>> list(@RequestParam(required = false) Integer status,
                                   @RequestParam(required = false) String keyword) {
        return Result.success(cityService.listByCondition(status, keyword));
    }
}
