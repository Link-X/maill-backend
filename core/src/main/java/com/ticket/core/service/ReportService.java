package com.ticket.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
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
import com.ticket.core.mapper.ReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * 统计报表服务。
 *
 * - 所有窗口语义统一：[start, end)（start 闭，end 开）
 * - 营收口径：仅 status IN (1, 5)，由 SQL 层保证
 * - 缓存：所有结果 5 分钟 Redis 缓存，key = report:{method}:{start}:{end}[:extra]
 */
@Slf4j
@Service
public class ReportService {

    /** Redis 缓存 TTL，5 分钟 */
    private static final long CACHE_TTL_SECONDS = 300;

    /** 用 Jackson 序列化报表结果存 Redis；JavaTimeModule 让 LocalDateTime 序列化为 ISO 字符串 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ReportMapper reportMapper;
    private final StringRedisTemplate redisTemplate;

    public ReportService(ReportMapper reportMapper, StringRedisTemplate redisTemplate) {
        this.reportMapper = reportMapper;
        this.redisTemplate = redisTemplate;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 时间窗口工具
    // ─────────────────────────────────────────────────────────────────────

    /** 解析后的时间窗口；end 不含 */
    public static class TimeRange {
        public final LocalDateTime start;
        public final LocalDateTime end;
        public TimeRange(LocalDateTime s, LocalDateTime e) { this.start = s; this.end = e; }
        public Duration duration() { return Duration.between(start, end); }
        /** 用作 Redis key 的稳定文本表示 */
        public String key() { return start + "_" + end; }
    }

    /**
     * 解析时间窗口：startTime/endTime 优先；否则按 range（1d/7d/30d/90d，默认 30d）。
     */
    public TimeRange resolveRange(String range, LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null) {
            if (!startTime.isBefore(endTime)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "startTime 必须早于 endTime");
            }
            return new TimeRange(startTime, endTime);
        }
        LocalDateTime now = LocalDateTime.now();
        int days;
        if (range == null) {
            days = 30;
        } else {
            switch (range) {
                case "1d": days = 1;  break;
                case "7d": days = 7;  break;
                case "30d": days = 30; break;
                case "90d": days = 90; break;
                default: throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "range 仅支持 1d/7d/30d/90d，或显式传 startTime+endTime");
            }
        }
        return new TimeRange(now.minusDays(days), now);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 11 个报表方法
    // ─────────────────────────────────────────────────────────────────────

    public OverviewVO overview(TimeRange tr) {
        return cached("overview:" + tr.key(), new TypeReference<OverviewVO>() {}, () -> {
            // 当前窗口
            BigDecimal revenue = nz(reportMapper.sumRevenue(tr.start, tr.end));
            int orderCount = nz(reportMapper.countPaidOrders(tr.start, tr.end));
            int pendingOrderCount = nz(reportMapper.countPendingOrders(tr.start, tr.end));
            BigDecimal refundAmount = nz(reportMapper.sumRefundAmount(tr.start, tr.end));
            int refundCount = nz(reportMapper.countRefundOrders(tr.start, tr.end));
            int soldCount = nz(reportMapper.countTicketsSold(tr.start, tr.end));
            int verifiedCount = nz(reportMapper.countTicketsVerified(tr.start, tr.end));

            // 上一等长周期，用于环比
            Duration window = tr.duration();
            LocalDateTime prevEnd = tr.start;
            LocalDateTime prevStart = prevEnd.minus(window);
            BigDecimal prevRevenue = nz(reportMapper.sumRevenue(prevStart, prevEnd));
            int prevOrderCount = nz(reportMapper.countPaidOrders(prevStart, prevEnd));

            OverviewVO vo = new OverviewVO();
            vo.setRevenue(revenue);
            vo.setOrderCount(orderCount);
            vo.setPendingOrderCount(pendingOrderCount);
            vo.setRefundAmount(refundAmount);
            vo.setRefundCount(refundCount);
            vo.setTicketSoldCount(soldCount);
            vo.setTicketVerifiedCount(verifiedCount);
            vo.setVerifyRate(rate(verifiedCount, soldCount));
            vo.setRevenueDeltaPct(deltaPct(prevRevenue, revenue));
            vo.setOrderCountDeltaPct(deltaPct(BigDecimal.valueOf(prevOrderCount), BigDecimal.valueOf(orderCount)));
            return vo;
        });
    }

    public List<TimeseriesPointVO> timeseries(TimeRange tr, String dim) {
        if (dim == null) dim = "day";
        // 跨度校验：hour 最多 7d，month 至少 90d
        Duration d = tr.duration();
        if ("hour".equals(dim) && d.toDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dim=hour 时窗口最多 7 天");
        }
        if ("month".equals(dim) && d.toDays() < 90) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dim=month 时窗口至少 90 天");
        }
        String finalDim = dim;
        return cached("timeseries:" + dim + ":" + tr.key(),
                new TypeReference<List<TimeseriesPointVO>>() {}, () -> {
                    switch (finalDim) {
                        case "hour":  return reportMapper.timeseriesByHour(tr.start, tr.end);
                        case "month": return reportMapper.timeseriesByMonth(tr.start, tr.end);
                        case "day":   return reportMapper.timeseriesByDay(tr.start, tr.end);
                        default: throw new BusinessException(ErrorCode.PARAM_ERROR,
                                "dim 仅支持 day/hour/month");
                    }
                });
    }

    public List<ByShowVO> byShow(TimeRange tr, String sort, int limit) {
        String finalSort = sort == null ? "revenue" : sort;
        int finalLimit = clampLimit(limit, 10, 50);
        return cached("byShow:" + finalSort + ":" + finalLimit + ":" + tr.key(),
                new TypeReference<List<ByShowVO>>() {},
                () -> reportMapper.byShow(tr.start, tr.end, finalSort, finalLimit));
    }

    public List<ByCategoryVO> byCategory(TimeRange tr) {
        return cached("byCategory:" + tr.key(),
                new TypeReference<List<ByCategoryVO>>() {},
                () -> reportMapper.byCategory(tr.start, tr.end));
    }

    public List<ByCityVO> byCity(TimeRange tr) {
        return cached("byCity:" + tr.key(),
                new TypeReference<List<ByCityVO>>() {},
                () -> reportMapper.byCity(tr.start, tr.end));
    }

    public List<StatusDistVO> statusDistribution(TimeRange tr) {
        return cached("statusDist:" + tr.key(),
                new TypeReference<List<StatusDistVO>>() {},
                () -> reportMapper.statusDistribution(tr.start, tr.end));
    }

    public List<HourDistVO> hourDistribution(TimeRange tr) {
        return cached("hourDist:" + tr.key(),
                new TypeReference<List<HourDistVO>>() {},
                () -> reportMapper.hourDistribution(tr.start, tr.end));
    }

    public List<BySessionVO> bySession(TimeRange tr, Long showId, String sort, int limit) {
        String finalSort = sort == null ? "fillRate" : sort;
        int finalLimit = clampLimit(limit, 20, 100);
        String suffix = (showId == null ? "" : showId.toString()) + ":" + finalSort + ":" + finalLimit;
        return cached("bySession:" + suffix + ":" + tr.key(),
                new TypeReference<List<BySessionVO>>() {},
                () -> reportMapper.bySession(tr.start, tr.end, showId, finalSort, finalLimit));
    }

    public UserStatsVO userStats(TimeRange tr) {
        return cached("userStats:" + tr.key(), new TypeReference<UserStatsVO>() {}, () -> {
            int totalBuyers = nz(reportMapper.countDistinctBuyers(tr.start, tr.end));
            int repeatBuyers = nz(reportMapper.countRepeatBuyers(tr.start, tr.end));
            int orderCount = nz(reportMapper.countPaidOrders(tr.start, tr.end));
            int soldCount = nz(reportMapper.countTicketsSold(tr.start, tr.end));
            BigDecimal revenue = nz(reportMapper.sumRevenue(tr.start, tr.end));

            UserStatsVO vo = new UserStatsVO();
            vo.setTotalBuyers(totalBuyers);
            vo.setRepeatBuyers(repeatBuyers);
            vo.setRepeatRate(rate(repeatBuyers, totalBuyers));
            vo.setAvgOrderValue(orderCount == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP));
            vo.setAvgSeatsPerOrder(orderCount == 0 ? 0.0 : (double) soldCount / orderCount);
            return vo;
        });
    }

    public RefundStatsVO refundStats(TimeRange tr) {
        return cached("refundStats:" + tr.key(), new TypeReference<RefundStatsVO>() {}, () -> {
            BigDecimal totalRevenue = nz(reportMapper.sumRevenue(tr.start, tr.end));
            BigDecimal refundAmount = nz(reportMapper.sumRefundAmount(tr.start, tr.end));
            int full = nz(reportMapper.countByStatus(tr.start, tr.end, 4));
            int partial = nz(reportMapper.countByStatus(tr.start, tr.end, 5));
            int refunding = nz(reportMapper.countByStatus(tr.start, tr.end, 3));

            RefundStatsVO vo = new RefundStatsVO();
            vo.setTotalRevenue(totalRevenue);
            vo.setRefundAmount(refundAmount);
            vo.setRefundRate(rateBd(refundAmount, totalRevenue));
            vo.setFullRefundCount(full);
            vo.setPartialRefundCount(partial);
            vo.setRefundingCount(refunding);
            return vo;
        });
    }

    public CancellationStatsVO cancellationStats(TimeRange tr) {
        return cached("cancelStats:" + tr.key(), new TypeReference<CancellationStatsVO>() {}, () -> {
            int created = nz(reportMapper.countCreatedOrders(tr.start, tr.end));
            int userCancelled = nz(reportMapper.countCancelledByReason(tr.start, tr.end, 0));
            int expiredCancelled = nz(reportMapper.countCancelledByReason(tr.start, tr.end, 1));

            CancellationStatsVO vo = new CancellationStatsVO();
            vo.setCreatedCount(created);
            vo.setExpiredCancelledCount(expiredCancelled);
            vo.setUserCancelledCount(userCancelled);
            vo.setCancelRate(rate(userCancelled + expiredCancelled, created));
            return vo;
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // 工具
    // ─────────────────────────────────────────────────────────────────────

    private <T> T cached(String suffix, TypeReference<T> typeRef, Supplier<T> loader) {
        String key = "report:" + suffix;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return MAPPER.readValue(cached, typeRef);
            }
        } catch (Exception e) {
            log.warn("报表缓存读取失败，回源 DB. key={}", key, e);
        }
        T value = loader.get();
        if (value != null) {
            try {
                redisTemplate.opsForValue().set(key,
                        MAPPER.writeValueAsString(value),
                        java.time.Duration.ofSeconds(CACHE_TTL_SECONDS));
            } catch (Exception e) {
                log.warn("报表缓存写入失败. key={}", key, e);
            }
        }
        return value;
    }

    /** Integer null → 0 */
    private static int nz(Integer v) { return v == null ? 0 : v; }
    /** BigDecimal null → 0 */
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 整数比率，分母 0 时返回 0 */
    private static double rate(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return Math.round((double) numerator / denominator * 10000.0) / 10000.0;
    }

    /** BigDecimal 比率 */
    private static double rateBd(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).doubleValue();
    }

    /** 环比，prev=0 时返回 0（不返回 Infinity） */
    private static double deltaPct(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return curr.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static int clampLimit(int requested, int defaultValue, int max) {
        if (requested <= 0) return defaultValue;
        return Math.min(requested, max);
    }
}
