package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.ShowListRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@NoLogin
@RestController
@RequestMapping("/api/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/list")
    public Result<?> listShows(@RequestBody ShowListRequest req) {
        var list = showService.listPaged(req.getName(), req.getCategoryId(), req.getCityCode(),
                req.getVenue(), req.getPage(), req.getSize());
        var total = showService.count(req.getName(), req.getCategoryId(), req.getCityCode(),
                req.getVenue());
        return Result.success(Map.of("total", total, "list", list));
    }

    @GetMapping("/{id}")
    public Result<?> getShow(@PathVariable Long id) {
        return Result.success(showService.getVOById(id));
    }


}
