package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.City;
import com.ticket.core.domain.entity.Room;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.domain.vo.AreaPriceVO;
import com.ticket.core.domain.vo.SeatColVO;
import com.ticket.core.domain.vo.SeatRowVO;
import com.ticket.core.domain.vo.SeatSectionVO;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.mapper.ShowSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场次服务：演出场次的 CRUD、发布开售、座位图聚合查询。
 *
 * 从 ShowService 拆出，遵循单一职责：
 * - Show 维度的 CRUD 留在 ShowService
 * - 场次 CRUD 与场次相关的聚合查询（座位图）集中在这里
 *
 * 与 RoomService 协作：创建场次时把 Room 模板的座位与价格区域复制过来。
 */
@Service
public class SessionService {

    private final ShowSessionMapper showSessionMapper;
    private final SeatInventoryService inventoryService;
    private final RoomService roomService;
    private final SeatStructureCache structureCache;

    public SessionService(ShowSessionMapper showSessionMapper,
                          SeatInventoryService inventoryService,
                          RoomService roomService,
                          SeatStructureCache structureCache) {
        this.showSessionMapper = showSessionMapper;
        this.inventoryService = inventoryService;
        this.roomService = roomService;
        this.structureCache = structureCache;
    }

    /**
     * 创建场次
     * 1. status 强制设为 0（未开售）
     * 2. 若指定 roomId，先从 Room 拷贝 rowCount/colCount 到 session（保证 insert 时即填充）
     * 3. insert 后调用 copyToSession 复制座位与价格区域，并用返回的座位数回填 total_seats
     */
    @Transactional(rollbackFor = Exception.class)
    public ShowSession create(ShowSession showSession) {
        LocalDateTime now = LocalDateTime.now();
        showSession.setStatus(0);
        showSession.setCreateTime(now);
        showSession.setUpdateTime(now);

        // 前置填充 Room 的行列数
        Room room = null;
        if (showSession.getRoomId() != null) {
            room = roomService.getRoom(showSession.getRoomId());
            if (room != null) {
                showSession.setRowCount(room.getRowCount());
                showSession.setColCount(room.getColCount());
            }
        }

        // NOT NULL DEFAULT 0 列兜底，避免 MyBatis 显式插 NULL
        if (showSession.getRowCount() == null) showSession.setRowCount(0);
        if (showSession.getColCount() == null) showSession.setColCount(0);
        if (showSession.getTotalSeats() == null) showSession.setTotalSeats(0);

        showSessionMapper.insert(showSession);

        // 复制座位 / 价格区域，用真实座位数回填 total_seats
        if (room != null) {
            int seatCount = roomService.copyToSession(showSession.getRoomId(), showSession.getId());
            showSession.setTotalSeats(seatCount);
            showSessionMapper.updateSeatInfo(showSession.getId(),
                    showSession.getRowCount(), showSession.getColCount(), seatCount);
        }
        return showSession;
    }

    public ShowSession update(ShowSession showSession) {
        showSession.setUpdateTime(LocalDateTime.now());
        showSessionMapper.update(showSession);
        return showSessionMapper.selectById(showSession.getId());
    }

    public ShowSession getById(Long id) {
        return showSessionMapper.selectById(id);
    }

    public List<ShowSession> listByShowId(Long showId) {
        return showSessionMapper.selectByShowId(showId);
    }

    public List<ShowSession> listPaged(Long showId, Integer status,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       int page, int size) {
        int offset = (page - 1) * size;
        return showSessionMapper.selectByCondition(showId, status, startTime, endTime, offset, size);
    }

    public int count(Long showId, Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        return showSessionMapper.countByCondition(showId, status, startTime, endTime);
    }

    /**
     * 场次完整座位图与价格区域 + 演出/城市概要信息。
     * 结构数据(座位/价格/场次/演出/城市)走本地缓存,避免热门场次刷新打 DB。
     * 座位 status 语义:
     *  - 场次 status=1(销售中):走 Redis 实时(0=可售 1=已锁 2=已售)
     *  - 场次 status!=1(未开放/已结束):统一返回 -1,前端按整体不可点渲染
     * 空格子(rowCount×colCount 中没有座位)以 type=0 占位
     */
    public SessionSeatResponse getSeatSection(Long sessionId) {
        // session 直查 DB(单行主键查询,毫秒级),不进缓存,保证 status 多实例间一致
        ShowSession session = showSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "场次不存在");
        }
        List<SeatArea> areas = structureCache.getAreas(sessionId);
        List<Seat> seats = structureCache.getSeats(sessionId);

        int rowCount = session.getRowCount() != null ? session.getRowCount() : 0;
        int colCount = session.getColCount() != null ? session.getColCount() : 0;

        Map<String, Seat> seatGrid = new HashMap<>();
        List<Long> seatIds = new ArrayList<>();
        for (Seat seat : seats) {
            seatGrid.put(seat.getRowNo() + ":" + seat.getColNo(), seat);
            seatIds.add(seat.getId());
        }

        boolean onSale = session.getStatus() != null && session.getStatus() == 1;
        Map<Long, Integer> statusMap = (onSale && !seatIds.isEmpty())
                ? inventoryService.batchGetSeatStatus(sessionId, seatIds)
                : new HashMap<>();

        List<AreaPriceVO> areaPriceList = new ArrayList<>();
        for (SeatArea area : areas) {
            AreaPriceVO vo = new AreaPriceVO();
            vo.setAreaId(area.getAreaId());
            vo.setPrice(area.getPrice().toPlainString());
            vo.setOriginPrice(area.getOriginPrice().toPlainString());
            areaPriceList.add(vo);
        }

        List<SeatRowVO> seatRows = new ArrayList<>();
        for (int row = 1; row <= rowCount; row++) {
            List<SeatColVO> columns = new ArrayList<>();
            for (int col = 1; col <= colCount; col++) {
                Seat seat = seatGrid.get(row + ":" + col);
                SeatColVO colVO = new SeatColVO();
                if (seat == null) {
                    colVO.setColId("");
                    colVO.setColNum("");
                    colVO.setSeatName(null);
                    colVO.setType(0);
                    colVO.setAreaId(null);
                    colVO.setStatus(null);
                } else {
                    colVO.setColId(String.valueOf(seat.getId()));
                    colVO.setColNum(String.valueOf(seat.getColNo()));
                    colVO.setSeatName(seat.getSeatName());
                    colVO.setType(seat.getType());
                    colVO.setAreaId(seat.getAreaId());
                    colVO.setStatus(onSale ? statusMap.getOrDefault(seat.getId(), 0) : -1);
                }
                columns.add(colVO);
            }
            SeatRowVO rowVO = new SeatRowVO();
            rowVO.setRowsId(String.valueOf(row));
            rowVO.setRowsNum(String.valueOf(row));
            rowVO.setColumns(columns);
            seatRows.add(rowVO);
        }

        SeatSectionVO seatSection = new SeatSectionVO();
        seatSection.setRowCount(rowCount);
        seatSection.setColumnCount(colCount);
        seatSection.setSeatRows(seatRows);

        SessionSeatResponse response = new SessionSeatResponse();
        response.setSession(session);
        response.setAreaPriceList(areaPriceList);
        response.setSeatSection(seatSection);

        if (session.getShowId() != null) {
            Show show = structureCache.getShow(session.getShowId());
            if (show != null) {
                response.setShowId(show.getId());
                response.setShowName(show.getName());
                response.setShowVenue(show.getVenue());
                response.setShowAddress(show.getAddress());
                response.setShowCityCode(show.getCityCode());
                response.setShowPosterUrl(show.getPosterUrl());
                if (show.getCityCode() != null) {
                    City city = structureCache.getCity(show.getCityCode());
                    if (city != null) {
                        response.setShowCityName(city.getName());
                    }
                }
            }
        }
        return response;
    }
}
