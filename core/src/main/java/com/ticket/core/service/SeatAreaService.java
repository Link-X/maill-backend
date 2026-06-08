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
        areas.forEach(a -> {
            a.setSessionId(sessionId);
            // saleMode/allocateStrategy 没传则给默认值(1=选座 / 1=连坐优先)
            if (a.getSaleMode() == null) a.setSaleMode(1);
            if (a.getAllocateStrategy() == null) a.setAllocateStrategy(1);
            // single/couple 总数由服务端按实际 seat 统计回写,前端传的不做信任
            a.setSingleTotal(0);
            a.setCoupleTotal(0);
        });
        if (!areas.isEmpty()) {
            seatAreaMapper.batchInsert(areas);
        }

        // 按实际 seat 数据回写 single_total / couple_total(派座流程依赖准确值校验票种)
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);
        if (!seats.isEmpty()) {
            java.util.Map<String, int[]> totals = inventoryService.computeAreaTotals(seats);
            for (SeatArea a : areas) {
                int[] t = totals.getOrDefault(a.getAreaId(), new int[]{0, 0});
                seatAreaMapper.updateTotals(sessionId, a.getAreaId(), t[0], t[1]);
            }
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheBroadcaster.invalidateAreas(sessionId);
                ShowSession session = showSessionMapper.selectById(sessionId);
                if (session == null || session.getStatus() == null) return;

                try {
                    List<SeatArea> latestAreas = seatAreaMapper.selectBySessionId(sessionId);
                    if (session.getStatus() == 0) {
                        // 未开售:warmup 由场次开售时由 SessionLifecycleScheduler 触发,这里什么都不做
                        return;
                    }
                    // 销售中:**只做增量价格/配置刷新**,绝不调 warmup。
                    // 原因:warmup 会重置 session:seats SET / area:pool ZSET / area:stock 计数器,
                    // 已售座位会被"复活"导致超卖。saveAreas 的合法语义只是改价格。
                    inventoryService.refreshAreaConfig(sessionId, latestAreas);
                } catch (Exception e) {
                    log.error("销售中场次 {} 改价后 Redis 同步失败,需运维介入", sessionId, e);
                }
            }
        });
    }

    /**
     * admin 修改区域售卖模式 / 派座策略(不重写价格)。
     *
     * <p>销售中拒绝:改 sale_mode 需要重建池子,而重建会让已售座位复活引起超卖。
     * 必须在场次未开售(status=0)或已结束(status=2)时操作。
     */
    @Transactional
    public void updateSaleConfig(Long sessionId, String areaId,
                                 Integer saleMode, Integer allocateStrategy) {
        if (saleMode == null || (saleMode != 1 && saleMode != 2)) {
            throw new IllegalArgumentException("saleMode 必须为 1 或 2");
        }
        if (allocateStrategy == null) allocateStrategy = 1;

        ShowSession session = showSessionMapper.selectById(sessionId);
        if (session != null && session.getStatus() != null && session.getStatus() == 1) {
            throw new com.ticket.common.exception.BusinessException(
                    com.ticket.common.exception.ErrorCode.PARAM_ERROR,
                    "场次销售中,禁止修改区域售卖模式;请先停售再操作");
        }

        seatAreaMapper.updateSaleConfig(sessionId, areaId, saleMode, allocateStrategy);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheBroadcaster.invalidateAreas(sessionId);
                // 未开售场次:warmup 由开售流转触发,此处无需操作
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
