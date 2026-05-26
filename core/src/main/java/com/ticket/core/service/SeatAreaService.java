package com.ticket.core.service;

import com.ticket.core.cache.CacheInvalidationBroadcaster;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
public class SeatAreaService {

    private final SeatAreaMapper seatAreaMapper;
    private final SeatMapper seatMapper;
    private final ShowSessionMapper showSessionMapper;
    private final SeatInventoryService inventoryService;
    private final CacheInvalidationBroadcaster cacheBroadcaster;

    public SeatAreaService(SeatAreaMapper seatAreaMapper,
                           SeatMapper seatMapper,
                           ShowSessionMapper showSessionMapper,
                           SeatInventoryService inventoryService,
                           CacheInvalidationBroadcaster cacheBroadcaster) {
        this.seatAreaMapper = seatAreaMapper;
        this.seatMapper = seatMapper;
        this.showSessionMapper = showSessionMapper;
        this.inventoryService = inventoryService;
        this.cacheBroadcaster = cacheBroadcaster;
    }

    /**
     * 保存场次价格区域(覆盖写:先删旧的再批量插入)。
     * 事务提交后:
     *  - 广播 Caffeine 价格缓存失效(各实例展示立即拿到新价)
     *  - 若场次已在销售中(status=1),自动覆盖 Redis 库存里的价格,避免出现
     *    "用户看到新价但实际按旧价收钱"。未开售场次不 warmup,等定时任务到点处理
     */
    @Transactional
    public void saveAreas(Long sessionId, List<SeatArea> areas) {
        seatAreaMapper.deleteBySessionId(sessionId);
        areas.forEach(a -> a.setSessionId(sessionId));
        if (!areas.isEmpty()) {
            seatAreaMapper.batchInsert(areas);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheBroadcaster.invalidateAreas(sessionId);
                ShowSession session = showSessionMapper.selectById(sessionId);
                if (session == null || session.getStatus() == null || session.getStatus() != 1) {
                    return;
                }
                try {
                    List<Seat> seats = seatMapper.selectBySessionId(sessionId);
                    if (seats.isEmpty()) return;
                    List<SeatArea> latestAreas = seatAreaMapper.selectBySessionId(sessionId);
                    inventoryService.warmup(sessionId, seats, latestAreas);
                } catch (Exception e) {
                    log.error("销售中场次 {} 改价后 Redis 同步失败,需运维介入", sessionId, e);
                }
            }
        });
    }

    /**
     * 查询场次价格区域列表
     */
    public List<SeatArea> getAreasBySession(Long sessionId) {
        return seatAreaMapper.selectBySessionId(sessionId);
    }
}
