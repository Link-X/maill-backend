package com.ticket.admin.controller;

import com.ticket.admin.dto.ArticleIdRequest;
import com.ticket.admin.dto.ArticleListRequest;
import com.ticket.admin.dto.ArticleSaveRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Article;
import com.ticket.core.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Tag(name = "资讯管理", description = "资讯 CRUD + 发布/下架")
@RestController
@RequestMapping("/api/admin/article")
public class ArticleController {

    private final ArticleService service;

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @Operation(summary = "列表(分页)")
    @PostMapping("/list")
    public Result<Map<String, Object>> list(@RequestBody ArticleListRequest req) {
        return Result.success(service.listByCondition(
                req.getStatus(), req.getCategoryId(), req.getKeyword(), req.getPage(), req.getSize()));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<Article> get(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @Operation(summary = "保存(id 为空=新增,否则=更新)")
    @PostMapping("/save")
    public Result<Article> save(@Valid @RequestBody ArticleSaveRequest req) {
        Article a = new Article();
        BeanUtils.copyProperties(req, a);
        return Result.success(service.save(a));
    }

    @Operation(summary = "发布")
    @PostMapping("/publish")
    public Result<Void> publish(@Valid @RequestBody ArticleIdRequest req) {
        service.publish(req.id);
        return Result.success(null);
    }

    @Operation(summary = "下架")
    @PostMapping("/offline")
    public Result<Void> offline(@Valid @RequestBody ArticleIdRequest req) {
        service.offline(req.id);
        return Result.success(null);
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(null);
    }
}
