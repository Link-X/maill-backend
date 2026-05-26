package com.ticket.admin.controller;

import com.ticket.admin.dto.BannerSaveRequest;
import com.ticket.admin.dto.BannerSortRequest;
import com.ticket.admin.dto.BannerStatusRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Banner;
import com.ticket.core.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "Banner 管理", description = "首页 Banner / 运营位 CRUD,支持图片(MinIO URL)、跳转配置、定时上下架、拖拽排序")
@RestController
@RequestMapping("/api/admin/banner")
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @Operation(summary = "Banner 列表",
               description = "管理端列表,按 sort ASC,id ASC 排序;不传 status 则返回全部")
    @GetMapping("/list")
    public Result<List<Banner>> list(
            @Parameter(description = "状态过滤:0=下架 1=上架;不传则全部") @RequestParam(required = false) Integer status) {
        return Result.success(bannerService.listByCondition(status));
    }

    @Operation(summary = "Banner 详情")
    @GetMapping("/{id}")
    public Result<Banner> get(@PathVariable Long id) {
        return Result.success(bannerService.getById(id));
    }

    @Operation(summary = "保存 Banner", description = "id 为空=新增,否则=更新")
    @PostMapping("/save")
    public Result<Banner> save(@Valid @RequestBody BannerSaveRequest req) {
        Banner banner = new Banner();
        BeanUtils.copyProperties(req, banner);
        return Result.success(bannerService.save(banner));
    }

    @Operation(summary = "上下架")
    @PostMapping("/status")
    public Result<Void> status(@Valid @RequestBody BannerStatusRequest req) {
        bannerService.updateStatus(req.getId(), req.getStatus());
        return Result.success(null);
    }

    @Operation(summary = "拖拽排序", description = "按 orderedIds 顺序重写 sort,步长 10")
    @PostMapping("/sort")
    public Result<Void> sort(@Valid @RequestBody BannerSortRequest req) {
        bannerService.reorder(req.getOrderedIds());
        return Result.success(null);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return Result.success(null);
    }
}
