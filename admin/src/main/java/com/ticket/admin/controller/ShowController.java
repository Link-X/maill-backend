package com.ticket.admin.controller;

import com.ticket.admin.dto.ShowCreateRequest;
import com.ticket.admin.dto.ShowUpdateRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.service.ShowService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/admin/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

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

    @GetMapping("/{id}")
    public Result<Show> getShow(@PathVariable Long id) {
        return Result.success(showService.getById(id));
    }

    @GetMapping("/list")
    public Result<?> listShows(@RequestParam(required = false) Integer status) {
        return Result.success(showService.listAll(status));
    }
}
