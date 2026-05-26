package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单 Mapper 接口
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单（自动生成主键）
     */
    int insert(Order order);

    /**
     * 根据 ID 查询订单
     */
    Order selectById(Long id);

    /**
     * 根据订单号查询订单
     */
    Order selectByOrderNo(String orderNo);

    /**
     * 更新订单状态（前置状态固定为 0，用于超时/主动取消未支付订单）
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 取消订单并写入原因（仅 status=0 才允许）
     * @param reason 0=用户主动 1=超时自动
     */
    int cancelWithReason(@Param("id") Long id, @Param("reason") Integer reason);

    /**
     * 累加已退款金额（多次部分退款时调用多次）
     */
    int addRefundAmount(@Param("id") Long id, @Param("incr") java.math.BigDecimal incr);

    /**
     * 条件更新订单状态（从 fromStatus 变更为 toStatus，乐观锁保护）
     */
    int updateStatusFrom(@Param("id") Long id,
                         @Param("fromStatus") Integer fromStatus,
                         @Param("toStatus") Integer toStatus);

    /**
     * 查询已过期待支付订单（status=0 且 expire_time < expireTime）
     */
    List<Order> selectExpiredOrders(@Param("status") Integer status,
                                    @Param("expireTime") LocalDateTime expireTime,
                                    @Param("limit") Integer limit);

    /**
     * 查询长时间停留在指定状态的订单(update_time 早于 updateBefore),
     * 用于退款补偿:扫描卡在退款中(status=3)的"僵尸"订单
     */
    List<Order> selectStuckOrders(@Param("status") Integer status,
                                  @Param("updateBefore") LocalDateTime updateBefore,
                                  @Param("limit") Integer limit);

    /**
     * 更新订单状态和支付时间
     */
    int updateStatusAndPayTime(@Param("id") Long id,
                               @Param("status") Integer status,
                               @Param("payTime") LocalDateTime payTime);

    /**
     * 按用户查询订单列表（按创建时间倒序，分页），startTime/endTime 可选
     */
    List<Order> selectByUserId(@Param("userId") Long userId,
                               @Param("offset") int offset,
                               @Param("limit") int limit,
                               @Param("status") Integer status,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    /**
     * 统计用户订单总数，status/startTime/endTime 可选
     */
    int countByUserId(@Param("userId") Long userId,
                      @Param("status") Integer status,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 管理端订单分页查询：支持 showId / sessionId / orderNo / status / 时间范围筛选。
     * showId 需要 JOIN show_session（订单只存 sessionId，演出维度需要二级关联）。
     */
    List<Order> selectByAdminCondition(@Param("showId") Long showId,
                                       @Param("sessionId") Long sessionId,
                                       @Param("orderNo") String orderNo,
                                       @Param("status") Integer status,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int countByAdminCondition(@Param("showId") Long showId,
                              @Param("sessionId") Long sessionId,
                              @Param("orderNo") String orderNo,
                              @Param("status") Integer status,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 校验用户是否拥有该演出的已观看订单(status=1 已支付 或 status=5 部分退款)。
     * 用于 REVIEW_MODE_WATCHED 评价权限校验。
     */
    int existsCompletedByUserAndShow(@Param("userId") Long userId,
                                     @Param("showId") Long showId);
}
