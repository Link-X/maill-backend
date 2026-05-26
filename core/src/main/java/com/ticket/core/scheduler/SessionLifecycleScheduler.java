package com.ticket.core.scheduler;

import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.ShowSessionMapper;
import com.ticket.core.service.SeatStructureCache;
import com.ticket.core.service.SeatInventoryService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 场次生命周期调度。
 *
 * 每分钟执行两件事:
 *  1. 开售流转:status=0 且到达 openSaleTime → warmup Redis 库存 → status=1
 *  2. 结束流转:status IN (0,1) 且 end_time 已过 → status=2
 *
 * 设计要点:
 *  - 开售前置校验座位/价格是否齐全,缺失则保持 status=0 并 error log,等管理员补齐
 *  - updateStatusFrom 带乐观锁,多实例并发不会重复执行
 *  - 1 分钟存在精度窗口,下单接口需额外做实时时间校验兜底
 *  - 不主动清理 Redis 库存键(warmup TTL=7 天自然过期),避免误删
 */
@Slf4j
@Component
public class SessionLifecycleScheduler {

    private final ShowSessionMapper showSessionMapper;
    private final SeatInventoryService inventoryService;
    private final SeatStructureCache structureCache;

    public SessionLifecycleScheduler(ShowSessionMapper showSessionMapper,
                                     SeatInventoryService inventoryService,
                                     SeatStructureCache structureCache) {
        this.showSessionMapper = showSessionMapper;
        this.inventoryService = inventoryService;
        this.structureCache = structureCache;
    }

    /** 每分钟开头执行 */
    @Scheduled(cron = "0 * * * * ?")
    @SchedulerLock(name = "SessionLifecycleScheduler.tick", lockAtLeastFor = "PT30S", lockAtMostFor = "PT55S")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        try {
            publishPending(now);
        } catch (Exception e) {
            log.error("场次开售流转任务异常", e);
        }
        try {
            endExpired(now);
        } catch (Exception e) {
            log.error("场次结束流转任务异常", e);
        }
    }

    private void publishPending(LocalDateTime now) {
        List<ShowSession> candidates = showSessionMapper.selectPendingPublish(now);
        if (candidates.isEmpty()) {
            return;
        }
        for (ShowSession session : candidates) {
            Long sid = session.getId();
            try {
                List<Seat> seats = structureCache.getSeats(sid);
                if (seats == null || seats.isEmpty()) {
                    log.error("场次 {} 已到开售时间但座位未配置,保持 status=0", sid);
                    continue;
                }
                List<SeatArea> areas = structureCache.getAreas(sid);
                if (areas == null || areas.isEmpty()) {
                    log.error("场次 {} 已到开售时间但价格区域未配置,保持 status=0", sid);
                    continue;
                }
                inventoryService.warmup(sid, seats, areas);
                int affected = showSessionMapper.updateStatusFrom(sid, 0, 1);
                if (affected > 0) {
                    log.info("场次 {} 自动开售完成(warmup + status 0→1)", sid);
                }
            } catch (Exception e) {
                log.error("场次 {} 开售流转失败,等待下次重试", sid, e);
            }
        }
    }

    private void endExpired(LocalDateTime now) {
        int affected = showSessionMapper.batchEndExpired(now);
        if (affected > 0) {
            log.info("场次结束流转 {} 个", affected);
        }
    }
}
