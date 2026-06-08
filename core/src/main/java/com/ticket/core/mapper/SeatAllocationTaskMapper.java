package com.ticket.core.mapper;

import com.ticket.core.domain.entity.SeatAllocationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 派座任务 Mapper
 */
@Mapper
public interface SeatAllocationTaskMapper {

    int insert(SeatAllocationTask task);

    SeatAllocationTask selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * CAS 更新状态(status 从 expectFrom 改为 expectTo);用于派座成功/失败/回滚的原子流转
     */
    int updateStatusFrom(@Param("orderNo") String orderNo,
                         @Param("expectFrom") Integer expectFrom,
                         @Param("expectTo") Integer expectTo,
                         @Param("allocatedSeats") String allocatedSeats,
                         @Param("failReason") String failReason);

    /**
     * 扫描超时未派座的任务(status=0 且 create_time < before),给定时回滚扫描器用
     */
    List<SeatAllocationTask> selectTimeoutPending(@Param("before") LocalDateTime before,
                                                  @Param("limit") int limit);

    /**
     * 仅写 allocated_seats(状态不动)。
     * 在 popFromPool 成功后立刻调用,作为"已派出"的持久化标记 —
     * 若消费者后续崩溃,scheduler 可据此判断需还池 vs 仅还 stock。
     */
    int updateAllocatedOnly(@Param("orderNo") String orderNo,
                            @Param("allocatedSeats") String allocatedSeats);
}
