package com.ticket.admin.service;

import com.ticket.core.cache.CacheInvalidationBroadcaster;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.mapper.SeatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * admin 端座位写入服务。
 *
 * 从 SeatController 抽出，让 Controller 不再直接持有 Mapper；
 * 同时保证 batchCreate 在事务中执行，将来扩展为多步写入(seat + seat_area + 库存预热)时
 * 能整体回滚。
 */
@Service
public class AdminSeatService {

    private final SeatMapper seatMapper;
    private final CacheInvalidationBroadcaster cacheBroadcaster;

    public AdminSeatService(SeatMapper seatMapper, CacheInvalidationBroadcaster cacheBroadcaster) {
        this.seatMapper = seatMapper;
        this.cacheBroadcaster = cacheBroadcaster;
    }

    @Transactional
    public List<Seat> batchCreate(Long sessionId, List<Seat> seats) {
        LocalDateTime now = LocalDateTime.now();
        seats.forEach(s -> {
            s.setSessionId(sessionId);
            s.setStatus(0);
            s.setCreateTime(now);
        });
        seatMapper.batchInsert(seats);
        // 广播到所有实例(含自己)失效座位缓存
        cacheBroadcaster.invalidateSeats(sessionId);
        return seats;
    }

    public List<Seat> listBySession(Long sessionId) {
        return seatMapper.selectBySessionId(sessionId);
    }
}
