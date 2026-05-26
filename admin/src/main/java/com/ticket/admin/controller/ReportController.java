package com.ticket.admin.controller;

import com.ticket.admin.dto.TimeRangeQuery;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "统计报表",
        description = "11 个聚合接口，覆盖营收 / 趋势 / 演出&分类&城市排行 / 状态&时段分布 / 场次售罄率 / 用户行为 / 退款&取消率。"
                + " 时间窗口：range=1d/7d/30d/90d 滚动，或 startTime+endTime 自定义。"
                + " 营收口径：仅 status IN (1 已支付, 5 部分退款)。"
                + " 所有结果 Redis 5 分钟缓存，高频刷新无压力。")
@RestController
@RequestMapping("/api/admin/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    private TimeRange tr(TimeRangeQuery q) {
        return reportService.resolveRange(q.getRange(), q.getStartTime(), q.getEndTime());
    }

    // ─────────── 第一档：概览 + 趋势 ───────────

    @Operation(summary = "营收概览", description = "首页 KPI 卡片用。包含营收、订单数、待支付、退款、票券售卖与核销，并附加与上一等长周期的环比（revenueDeltaPct / orderCountDeltaPct，0.15 = 增长 15%，负值为下降）")
    @GetMapping("/overview")
    public Result<OverviewVO> overview(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.overview(tr(q)));
    }

    @Operation(summary = "时间趋势", description = "按 day / hour / month 聚合。dim=hour 时窗口最多 7d，dim=month 时至少 90d（否则返回 PARAM_ERROR）。返回点的 date 字段格式随 dim：day→2026-05-23，hour→2026-05-23T14，month→2026-05")
    @GetMapping("/timeseries")
    public Result<List<TimeseriesPointVO>> timeseries(
            @ParameterObject TimeRangeQuery q,
            @Parameter(description = "聚合维度：day / hour / month，默认 day") @RequestParam(required = false, defaultValue = "day") String dim) {
        return Result.success(reportService.timeseries(tr(q), dim));
    }

    // ─────────── 第二档：分维度 ───────────

    @Operation(summary = "演出排行 Top N", description = "按 sort 字段排序的 Top N 演出。返回字段含 categoryName、cityName，前端无需再 join 分类/城市表")
    @GetMapping("/by-show")
    public Result<List<ByShowVO>> byShow(
            @ParameterObject TimeRangeQuery q,
            @Parameter(description = "Top N，默认 10，最大 50") @RequestParam(required = false, defaultValue = "10") int limit,
            @Parameter(description = "排序：revenue（默认）/ tickets / orderCount") @RequestParam(required = false, defaultValue = "revenue") String sort) {
        return Result.success(reportService.byShow(tr(q), sort, limit));
    }

    @Operation(summary = "按分类汇总", description = "首页饼图用。返回每个分类的订单数、票数、营收。未关联分类的演出会以 categoryId=null 单独一行")
    @GetMapping("/by-category")
    public Result<List<ByCategoryVO>> byCategory(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.byCategory(tr(q)));
    }

    @Operation(summary = "按城市汇总", description = "切换城市 tab 时的数据源")
    @GetMapping("/by-city")
    public Result<List<ByCityVO>> byCity(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.byCity(tr(q)));
    }

    @Operation(summary = "订单状态分布", description = "返回全部 6 个状态（0/1/2/3/4/5），不存在的也返回 count=0。totalAmountSum 对 0/2 状态不是营收，仅供参考；营收建议只取 status=1 和 status=5 的 sum")
    @GetMapping("/status-distribution")
    public Result<List<StatusDistVO>> statusDistribution(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.statusDistribution(tr(q)));
    }

    @Operation(summary = "24 小时下单分布", description = "返回 0-23 共 24 行，按下单时间的小时统计。前端画热力图/柱状图用")
    @GetMapping("/hour-distribution")
    public Result<List<HourDistVO>> hourDistribution(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.hourDistribution(tr(q)));
    }

    // ─────────── 第三档：精细指标 ───────────

    @Operation(summary = "场次售罄率排行",
            description = "窗口按 session.start_time 过滤（即'本期开演的场次'）；soldSeats=ticket WHERE status IN (0,1)，已退款的不算占座；fillRate=soldSeats/totalSeats（0~1）")
    @GetMapping("/by-session")
    public Result<List<BySessionVO>> bySession(
            @ParameterObject TimeRangeQuery q,
            @Parameter(description = "可选：只看某个演出的场次") @RequestParam(required = false) Long showId,
            @Parameter(description = "Top N，默认 20，最大 100") @RequestParam(required = false, defaultValue = "20") int limit,
            @Parameter(description = "排序：fillRate（默认 desc）/ revenue / startTime") @RequestParam(required = false, defaultValue = "fillRate") String sort) {
        return Result.success(reportService.bySession(tr(q), showId, sort, limit));
    }

    @Operation(summary = "用户购买行为", description = "总买家数（去重）、复购买家数（>=2 次）、复购率、客单价、平均座位数")
    @GetMapping("/user-stats")
    public Result<UserStatsVO> userStats(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.userStats(tr(q)));
    }

    @Operation(summary = "退款指标", description = "退款总额（读 order.refund_amount 累加值）、退款率、各状态退款订单数（refunding/full/partial）")
    @GetMapping("/refund-stats")
    public Result<RefundStatsVO> refundStats(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.refundStats(tr(q)));
    }

    @Operation(summary = "取消率分析", description = "区分超时取消（cancel_reason=1）和用户主动取消（cancel_reason=0）。cancelRate = (expired + user) / created")
    @GetMapping("/cancellation-stats")
    public Result<CancellationStatsVO> cancellationStats(@ParameterObject TimeRangeQuery q) {
        return Result.success(reportService.cancellationStats(tr(q)));
    }
}
