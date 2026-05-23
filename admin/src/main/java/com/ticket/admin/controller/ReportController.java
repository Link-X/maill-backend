package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.report.ByCategoryVO;
import com.ticket.core.domain.vo.report.ByCityVO;
import com.ticket.core.domain.vo.report.BySessionVO;
import com.ticket.core.domain.vo.report.ByShowVO;
import com.ticket.core.domain.vo.report.CancellationStatsVO;
import com.ticket.core.domain.vo.report.HourDistVO;
import com.ticket.core.domain.vo.report.OverviewVO;
import com.ticket.core.domain.vo.report.RefundStatsVO;
import com.ticket.core.domain.vo.report.StatusDistVO;
import com.ticket.core.domain.vo.report.TimeseriesPointVO;
import com.ticket.core.domain.vo.report.UserStatsVO;
import com.ticket.core.service.ReportService;
import com.ticket.core.service.ReportService.TimeRange;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计报表（管理端）
 *
 * 所有接口接受三套参数（互斥）：
 *   - range = 1d/7d/30d/90d（默认 30d）
 *   - startTime + endTime 自定义（ISO 8601 LocalDateTime）
 *
 * 所有结果经 Redis 5 分钟缓存。详细字段约定见 docs/report-api-spec（or Swagger）。
 */
@RestController
@RequestMapping("/api/admin/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    private TimeRange tr(String range, LocalDateTime startTime, LocalDateTime endTime) {
        return reportService.resolveRange(range, startTime, endTime);
    }

    // ─────────── 第一档：概览 + 趋势 ───────────

    @GetMapping("/overview")
    public Result<OverviewVO> overview(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.overview(tr(range, startTime, endTime)));
    }

    @GetMapping("/timeseries")
    public Result<List<TimeseriesPointVO>> timeseries(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "day") String dim) {
        return Result.success(reportService.timeseries(tr(range, startTime, endTime), dim));
    }

    // ─────────── 第二档：分维度 ───────────

    @GetMapping("/by-show")
    public Result<List<ByShowVO>> byShow(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "revenue") String sort) {
        return Result.success(reportService.byShow(tr(range, startTime, endTime), sort, limit));
    }

    @GetMapping("/by-category")
    public Result<List<ByCategoryVO>> byCategory(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.byCategory(tr(range, startTime, endTime)));
    }

    @GetMapping("/by-city")
    public Result<List<ByCityVO>> byCity(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.byCity(tr(range, startTime, endTime)));
    }

    @GetMapping("/status-distribution")
    public Result<List<StatusDistVO>> statusDistribution(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.statusDistribution(tr(range, startTime, endTime)));
    }

    @GetMapping("/hour-distribution")
    public Result<List<HourDistVO>> hourDistribution(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.hourDistribution(tr(range, startTime, endTime)));
    }

    // ─────────── 第三档：精细指标 ───────────

    @GetMapping("/by-session")
    public Result<List<BySessionVO>> bySession(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long showId,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "fillRate") String sort) {
        return Result.success(reportService.bySession(tr(range, startTime, endTime), showId, sort, limit));
    }

    @GetMapping("/user-stats")
    public Result<UserStatsVO> userStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.userStats(tr(range, startTime, endTime)));
    }

    @GetMapping("/refund-stats")
    public Result<RefundStatsVO> refundStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.refundStats(tr(range, startTime, endTime)));
    }

    @GetMapping("/cancellation-stats")
    public Result<CancellationStatsVO> cancellationStats(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(reportService.cancellationStats(tr(range, startTime, endTime)));
    }
}
