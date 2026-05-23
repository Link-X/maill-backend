package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.City;
import com.ticket.core.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "城市（只读）", description = "城市数据由 schema.sql seed 30 个主要城市（GB/T 行政区划代码），后端不开放写入；本接口供后台创建演出时下拉选择使用")
@RestController
@RequestMapping("/api/admin/city")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @Operation(summary = "城市列表", description = "按 sort 升序返回。可按 status 和 name 前缀筛选")
    @GetMapping("/list")
    public Result<List<City>> list(
            @Parameter(description = "状态：0=禁用 1=启用；不传则返回全部") @RequestParam(required = false) Integer status,
            @Parameter(description = "城市名前缀模糊匹配，例如传 '北' 匹配 '北京'") @RequestParam(required = false) String keyword) {
        return Result.success(cityService.listByCondition(status, keyword));
    }
}
