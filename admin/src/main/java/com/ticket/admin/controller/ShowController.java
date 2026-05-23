package com.ticket.admin.controller;

import com.ticket.admin.dto.ShowCreateRequest;
import com.ticket.admin.dto.ShowUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "演出管理", description = "演出 CRUD。Request DTO 严格校验：前端不能传 id / status / createTime / updateTime（由后端管理）")
@RestController
@RequestMapping("/api/admin/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @Operation(summary = "创建演出", description = "status 由后端强制为 1（已上架）。海报 URL 可先调 /api/admin/upload/image 拿到后再填入此处")
    @PostMapping("/create")
    public Result<Show> createShow(@Valid @RequestBody ShowCreateRequest req) {
        Show show = new Show();
        show.setName(req.getName());
        show.setDescription(req.getDescription());
        show.setCategoryId(req.getCategoryId());
        show.setCityCode(req.getCityCode());
        show.setAddress(req.getAddress());
        show.setVenue(req.getVenue());
        show.setPosterUrl(req.getPosterUrl());
        show.setExtend(req.getExtend());
        // status 由 service 强制为 1；id / createTime / updateTime 由 service 生成
        return Result.success(showService.create(show));
    }

    @Operation(summary = "更新演出", description = "支持改 status：0=草稿 1=已上架 2=已下架")
    @PutMapping("/update")
    public Result<Show> updateShow(@Valid @RequestBody ShowUpdateRequest req) {
        Show show = new Show();
        show.setId(req.getId());
        show.setName(req.getName());
        show.setDescription(req.getDescription());
        show.setCategoryId(req.getCategoryId());
        show.setCityCode(req.getCityCode());
        show.setAddress(req.getAddress());
        show.setVenue(req.getVenue());
        show.setPosterUrl(req.getPosterUrl());
        show.setExtend(req.getExtend());
        show.setStatus(req.getStatus());
        return Result.success(showService.update(show));
    }

    @Operation(summary = "演出详情（原始 Show 实体）", description = "若需要 categoryName / cityName，请用用户端 /api/show/{id} 的 ShowVO")
    @GetMapping("/{id}")
    public Result<Show> getShow(@Parameter(description = "演出 ID") @PathVariable Long id) {
        return Result.success(showService.getById(id));
    }

    @Operation(summary = "演出列表", description = "可按 status 过滤；不分页，全量返回（按 createTime 倒序）")
    @GetMapping("/list")
    public Result<?> listShows(
            @Parameter(description = "状态过滤 0/1/2；不传则返回全部") @RequestParam(required = false) Integer status) {
        return Result.success(showService.listAll(status));
    }
}
