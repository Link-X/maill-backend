package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.City;
import com.ticket.core.service.CityService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "城市（用户端）", description = "用户端首页城市切换 tabs；30 个主要城市由 schema.sql seed")
@NoLogin
@RestController
@RequestMapping("/api/city")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @Operation(summary = "启用的城市列表", description = "仅返回 status=1，按 sort 升序")
    @SecurityRequirements({})
    @GetMapping("/list")
    public Result<List<City>> list() {
        return Result.success(cityService.listEnabled());
    }
}
