package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.ShowListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "演出（用户端）", description = "演出列表与详情；返回 ShowVO（含 categoryName / cityName / address / extend），前端无需再 join")
@NoLogin
@RestController
@RequestMapping("/api/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @Operation(summary = "上架演出列表", description = "分页（page 从 1 开始，size 1~100）；可按 name / categoryId / cityCode / venue 筛选；name/venue 走前缀模糊匹配，categoryId/cityCode 精确。返回 list 每条是 ShowVO")
    @SecurityRequirements({})
    @PostMapping("/list")
    public Result<?> listShows(@RequestBody ShowListRequest req) {
        var list = showService.listPaged(req.getName(), req.getCategoryId(), req.getCityCode(),
                req.getVenue(), req.getPage(), req.getSize());
        var total = showService.count(req.getName(), req.getCategoryId(), req.getCityCode(),
                req.getVenue());
        return Result.success(Map.of("total", total, "list", list));
    }

    @Operation(summary = "演出详情", description = "返回 ShowVO，含 categoryName / cityName / address / extend")
    @SecurityRequirements({})
    @GetMapping("/{id}")
    public Result<?> getShow(@Parameter(description = "演出 ID") @PathVariable Long id) {
        return Result.success(showService.getVOById(id));
    }


}
