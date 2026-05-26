package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Banner;
import com.ticket.core.service.BannerService;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Banner", description = "首页 Banner / 运营位,只返回当前有效(已上架且在时间窗内)的 Banner")
@NoLogin
@RestController
@RequestMapping("/api/banner")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "首页有效 Banner 列表",
               description = "status=1 且 start_at <= NOW <= end_at(NULL 视为无限),按 sort ASC 排序")
    @GetMapping("/list")
    public Result<List<Banner>> list() {
        return Result.success(bannerService.listEffective());
    }
}
