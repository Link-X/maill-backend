package com.ticket.core.mapper;

import com.ticket.core.domain.vo.report.ByCategoryVO;
import com.ticket.core.domain.vo.report.ByCityVO;
import com.ticket.core.domain.vo.report.BySessionVO;
import com.ticket.core.domain.vo.report.ByShowVO;
import com.ticket.core.domain.vo.report.HourDistVO;
import com.ticket.core.domain.vo.report.StatusDistVO;
import com.ticket.core.domain.vo.report.TimeseriesPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报表聚合查询 Mapper。
 *
 * 时间窗口统一用 order.create_time 过滤（idx_create_time 已建索引）；
 * by-session 是例外，按 show_session.start_time 过滤。
 *
 * 营收口径：仅 status IN (1, 5)。
 */
@Mapper
public interface ReportMapper {

    // ─── overview ─────────────────────────────────────

    /** 已支付订单营收（SUM total_amount WHERE status IN (1,5)） */
    BigDecimal sumRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 已支付订单数 */
    Integer countPaidOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 待支付订单数（status=0） */
    Integer countPendingOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 退款总额（SUM refund_amount） */
    BigDecimal sumRefundAmount(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 退款订单数（status IN 3/4/5） */
    Integer countRefundOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 已售票数 = 仅算 status=1/5 订单的 order_item 行数 */
    Integer countTicketsSold(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 已核销票数 = ticket.status=1 且其订单在窗口内 */
    Integer countTicketsVerified(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ─── timeseries ───────────────────────────────────

    /** dim=day */
    List<TimeseriesPointVO> timeseriesByDay(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** dim=hour */
    List<TimeseriesPointVO> timeseriesByHour(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /** dim=month */
    List<TimeseriesPointVO> timeseriesByMonth(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // ─── by-show / by-category / by-city ──────────────

    List<ByShowVO> byShow(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("sort") String sort,
                          @Param("limit") int limit);

    List<ByCategoryVO> byCategory(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ByCityVO> byCity(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ─── distributions ────────────────────────────────

    List<StatusDistVO> statusDistribution(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    List<HourDistVO> hourDistribution(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    // ─── by-session ───────────────────────────────────

    List<BySessionVO> bySession(@Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end,
                                @Param("showId") Long showId,
                                @Param("sort") String sort,
                                @Param("limit") int limit);

    // ─── user-stats ───────────────────────────────────

    /** 下过单（status=1/5）的去重用户数 */
    Integer countDistinctBuyers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 同期内购买 >=2 次的用户数 */
    Integer countRepeatBuyers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ─── cancellation-stats ───────────────────────────

    /** 期间创建的订单总数（所有 status） */
    Integer countCreatedOrders(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** status=2 且 cancel_reason 指定值 */
    Integer countCancelledByReason(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   @Param("reason") Integer reason);

    // ─── refund-stats（status 计数） ───────────────────

    Integer countByStatus(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("status") Integer status);
}
