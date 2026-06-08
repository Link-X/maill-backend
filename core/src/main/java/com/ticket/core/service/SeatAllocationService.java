package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.SeatAllocationTask;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.mapper.SeatAreaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 派座服务 — 基于"两段式"协议(同步扣库存 + 异步取池)实现派座流程。
 *
 * <p>核心保证:
 * <ul>
 *   <li>同步阶段({@link #reserveStock}):仅原子 DECR 库存计数器,不动池子。失败立即返回,
 *       适合抢购洪峰高频拒绝。</li>
 *   <li>异步阶段({@link #allocate}):ZPOPMIN 取最前 N 个 member。池里 member 在 warmup 时
 *       按 score = rowNo*100000 + colNo 升序排好,所以取出的是"前排靠左"的座位,
 *       对终端用户体验等价于连坐策略(同排几乎一定连续)。</li>
 *   <li>情侣对池的 member 形如 "leftId:rightId",派对操作是 ZPOPMIN 单个 member,
 *       拿到后拆出 2 个 seatId — 一对始终原子地一起取出,绝不可能被拆散派给单座用户。</li>
 * </ul>
 */
@Slf4j
@Service
public class SeatAllocationService {

    /** 同步阶段返回:仅承载意图,不持有座位 */
    public static class StockReservation {
        public final long sessionId;
        public final String areaId;
        public final int ticketType;
        public final int quantity;

        public StockReservation(long sessionId, String areaId, int ticketType, int quantity) {
            this.sessionId = sessionId;
            this.areaId = areaId;
            this.ticketType = ticketType;
            this.quantity = quantity;
        }
    }

    /** 异步阶段返回:已派出的座位 + member→score 反查表(供退款原位还池) */
    public static class AllocationResult {
        public final List<Long> seatIds;
        public final Map<String, Double> memberToScore;

        public AllocationResult(List<Long> seatIds, Map<String, Double> memberToScore) {
            this.seatIds = seatIds;
            this.memberToScore = memberToScore;
        }
    }

    private final SeatInventoryService inventoryService;
    private final SeatAreaMapper seatAreaMapper;

    public SeatAllocationService(SeatInventoryService inventoryService,
                                 SeatAreaMapper seatAreaMapper) {
        this.inventoryService = inventoryService;
        this.seatAreaMapper = seatAreaMapper;
    }

    /**
     * 同步阶段:校验区域+票种,原子扣库存。
     *
     * <p>抢购热点路径 — 优先走 Redis 缓存读 saleMode/totals(亚毫秒),
     * 未命中才回退 DB(冷启动 / TTL 过期 / 缓存 evict 等),并把结果写回 Redis 自愈。
     *
     * @throws BusinessException 区域不存在 / 非派座模式 / 票种与区域不匹配 / 库存不足
     */
    public StockReservation reserveStock(long sessionId, String areaId,
                                         int ticketType, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "购买数量必须大于 0");
        }
        if (ticketType != SeatAllocationTask.TICKET_TYPE_SINGLE
                && ticketType != SeatAllocationTask.TICKET_TYPE_COUPLE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "票种参数非法");
        }

        SeatInventoryService.AreaSaleConfig cfg = inventoryService.getAreaSaleConfig(sessionId, areaId);
        if (cfg == null) {
            // Redis 未命中 — DB 兜底 + 写回缓存
            SeatArea area = seatAreaMapper.selectBySessionAndArea(sessionId, areaId);
            if (area == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "区域不存在");
            }
            inventoryService.writeAreaSaleConfigBack(sessionId, areaId, area);
            cfg = new SeatInventoryService.AreaSaleConfig(
                    area.getSaleMode() != null ? area.getSaleMode() : 1,
                    area.getSingleTotal() != null ? area.getSingleTotal() : 0,
                    area.getCoupleTotal() != null ? area.getCoupleTotal() : 0);
        }

        if (cfg.saleMode != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该区域非派座模式");
        }
        if (ticketType == SeatAllocationTask.TICKET_TYPE_COUPLE && cfg.coupleTotal == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该区域无情侣座");
        }
        if (ticketType == SeatAllocationTask.TICKET_TYPE_SINGLE && cfg.singleTotal == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该区域无单座");
        }

        boolean ok = inventoryService.reserveAreaStock(sessionId, areaId, ticketType, quantity);
        if (!ok) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        return new StockReservation(sessionId, areaId, ticketType, quantity);
    }

    /**
     * 异步阶段:从池里 ZPOPMIN 取 quantity 个 member,转换为 seatId 列表。
     * 调用前提:reserveStock 已经成功扣减了库存。
     *
     * @throws BusinessException 池子里 member 数 &lt; quantity(数据不一致,上层应回滚库存)
     */
    public AllocationResult allocate(StockReservation reservation) {
        List<String> raw = inventoryService.popFromPool(
                reservation.sessionId, reservation.areaId,
                reservation.ticketType, reservation.quantity);

        if (raw.isEmpty()) {
            log.warn("[ALLOCATE] 池子取座失败,sessionId={} areaId={} ticketType={} qty={}",
                    reservation.sessionId, reservation.areaId,
                    reservation.ticketType, reservation.quantity);
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        // raw 形如 [member1, score1, member2, score2, ...]
        if (raw.size() % 2 != 0) {
            log.error("[ALLOCATE] popFromPool 返回长度异常={}", raw.size());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "派座数据异常");
        }
        Map<String, Double> memberToScore = new HashMap<>();
        List<Long> seatIds = new ArrayList<>();
        for (int i = 0; i < raw.size(); i += 2) {
            String member = raw.get(i);
            double score = Double.parseDouble(raw.get(i + 1));
            memberToScore.put(member, score);

            if (reservation.ticketType == SeatAllocationTask.TICKET_TYPE_SINGLE) {
                seatIds.add(Long.parseLong(member));
            } else {
                // member = "leftId:rightId"
                int idx = member.indexOf(':');
                if (idx < 0) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "情侣 member 格式异常");
                }
                seatIds.add(Long.parseLong(member.substring(0, idx)));
                seatIds.add(Long.parseLong(member.substring(idx + 1)));
            }
        }
        return new AllocationResult(seatIds, memberToScore);
    }

    /**
     * 回滚:把已派的座位还回池(派座后建单失败 / 定时任务回滚均会调用)。
     */
    public void rollbackAllocation(long sessionId, String areaId, int ticketType,
                                   Map<String, Double> memberToScore) {
        if (memberToScore == null || memberToScore.isEmpty()) return;
        inventoryService.releaseToPool(sessionId, areaId, ticketType, memberToScore);
    }

    /**
     * 仅回滚库存(扣库存成功但还没取池的回滚)
     */
    public void rollbackStockOnly(long sessionId, String areaId, int ticketType, int quantity) {
        inventoryService.rollbackAreaStock(sessionId, areaId, ticketType, quantity);
    }

    /**
     * 退款释放 — 把订单中已售的派座区座位还回对应池子。
     *
     * @param sessionId  场次
     * @param areaId     区域(同 area 内)
     * @param ticketType 票种
     * @param seatIds    要释放的 seatId 列表;情侣对必须成对(2k 个)
     * @param pairInfo   情侣对配对信息:seatId -> [leftId, rightId];SINGLE 时可传 null
     */
    public void releaseSeatsToPool(long sessionId, String areaId, int ticketType,
                                   List<Long> seatIds, Map<Long, long[]> pairInfo) {
        if (seatIds == null || seatIds.isEmpty()) return;

        Map<String, Double> members = new HashMap<>();
        if (ticketType == SeatAllocationTask.TICKET_TYPE_SINGLE) {
            for (Long sid : seatIds) {
                members.put(String.valueOf(sid), readScoreFromInfo(sid));
            }
        } else {
            if (seatIds.size() % 2 != 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "情侣座必须成对退款");
            }
            if (pairInfo == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少情侣座配对信息");
            }
            Set<Long> consumed = new HashSet<>();
            for (Long sid : seatIds) {
                if (consumed.contains(sid)) continue;
                long[] pair = pairInfo.get(sid);
                if (pair == null || pair.length < 2) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少情侣座配对信息");
                }
                long leftId  = Math.min(pair[0], pair[1]);
                long rightId = Math.max(pair[0], pair[1]);
                if (!consumed.add(leftId) || !consumed.add(rightId)) {
                    // 已处理过这对
                    continue;
                }
                String member = leftId + ":" + rightId;
                members.put(member, readScoreFromInfo(leftId));
            }
        }
        inventoryService.releaseToPool(sessionId, areaId, ticketType, members);
    }

    /** 从 seat:info 重算 score(rowNo*100000 + colNo);读不到返回 0(座位会沉到池子最前) */
    private double readScoreFromInfo(long seatId) {
        Map<Object, Object> info = inventoryService.getSeatInfo(seatId);
        if (info == null || info.isEmpty()) return 0;
        Object r = info.get("row");
        Object c = info.get("col");
        if (r == null || c == null) return 0;
        try {
            int row = Integer.parseInt(String.valueOf(r));
            int col = Integer.parseInt(String.valueOf(c));
            return (double) row * 100000 + col;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
