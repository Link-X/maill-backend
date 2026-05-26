package com.ticket.admin.controller;

import com.ticket.admin.dto.ArtistSaveRequest;
import com.ticket.admin.dto.ArtistStatusRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Artist;
import com.ticket.core.service.ArtistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Tag(name = "艺人管理", description = "艺人 CRUD,可通过 show_artist 关联到演出")
@RestController
@RequestMapping("/api/admin/artist")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @Operation(summary = "艺人列表")
    @GetMapping("/list")
    public Result<List<Artist>> list(
            @Parameter(description = "0=下架 1=上架;不传则全部") @RequestParam(required = false) Integer status,
            @Parameter(description = "name/stage_name 前缀模糊") @RequestParam(required = false) String keyword) {
        return Result.success(artistService.listByCondition(status, keyword));
    }

    @Operation(summary = "艺人详情")
    @GetMapping("/{id}")
    public Result<Artist> get(@PathVariable Long id) {
        return Result.success(artistService.getById(id));
    }

    @Operation(summary = "保存艺人(id 为空=新增,否则=更新)")
    @PostMapping("/save")
    public Result<Artist> save(@Valid @RequestBody ArtistSaveRequest req) {
        Artist artist = new Artist();
        BeanUtils.copyProperties(req, artist);
        return Result.success(artistService.save(artist));
    }

    @Operation(summary = "上下架")
    @PostMapping("/status")
    public Result<Void> status(@Valid @RequestBody ArtistStatusRequest req) {
        artistService.updateStatus(req.getId(), req.getStatus());
        return Result.success(null);
    }

    @Operation(summary = "删除艺人(不级联删除关注/演出关联)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        artistService.delete(id);
        return Result.success(null);
    }
}
