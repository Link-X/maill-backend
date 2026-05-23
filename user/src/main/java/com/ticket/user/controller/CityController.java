package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.City;
import com.ticket.core.service.CityService;
import com.ticket.user.config.NoLogin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 城市列表（用户端）：仅返回启用城市，供首页 tabs 使用
 */
@NoLogin
@RestController
@RequestMapping("/api/city")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping("/list")
    public Result<List<City>> list() {
        return Result.success(cityService.listEnabled());
    }
}
