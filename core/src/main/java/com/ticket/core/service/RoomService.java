package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Room;
import com.ticket.core.domain.entity.RoomArea;
import com.ticket.core.domain.entity.RoomSeat;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.vo.RoomTemplateVO;
import com.ticket.core.mapper.RoomAreaMapper;
import com.ticket.core.mapper.RoomMapper;
import com.ticket.core.mapper.RoomSeatMapper;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final RoomMapper roomMapper;
    private final RoomSeatMapper roomSeatMapper;
    private final RoomAreaMapper roomAreaMapper;
    private final SeatMapper seatMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final ShowSessionMapper showSessionMapper;

    public RoomService(RoomMapper roomMapper,
                       RoomSeatMapper roomSeatMapper,
                       RoomAreaMapper roomAreaMapper,
                       SeatMapper seatMapper,
                       SeatAreaMapper seatAreaMapper,
                       ShowSessionMapper showSessionMapper) {
        this.roomMapper = roomMapper;
        this.roomSeatMapper = roomSeatMapper;
        this.roomAreaMapper = roomAreaMapper;
        this.seatMapper = seatMapper;
        this.seatAreaMapper = seatAreaMapper;
        this.showSessionMapper = showSessionMapper;
    }

    @Transactional
    public Room createRoom(Room room) {
        LocalDateTime now = LocalDateTime.now();
        room.setCreateTime(now);
        room.setUpdateTime(now);
        roomMapper.insert(room);
        return room;
    }

    @Transactional
    public Room updateRoom(Room room) {
        room.setUpdateTime(LocalDateTime.now());
        roomMapper.update(room);
        return roomMapper.selectById(room.getId());
    }

    /**
     * 删除场地。
     *
     * <p>引用约束:任意 show_session.room_id = 该场地 → 拒绝删除(返回 ROOM_IN_USE)。
     * 即使被引用的 session 已结束也保留场地数据,因为 session 自身的 room_id 字段在历史上仍指向它,
     * 删掉会让 admin 后台订单/统计页面的关联回查变 NULL,造成数据可读性下降。
     *
     * <p>未被引用 → 同事务级联删除 room / room_seat / room_area。模板与已创建场次的座位已是独立副本,
     * 删模板不影响任何在售场次。
     */
    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
        int referenced = showSessionMapper.countByRoomId(roomId);
        if (referenced > 0) {
            throw new BusinessException(ErrorCode.ROOM_IN_USE);
        }
        roomSeatMapper.deleteByRoomId(roomId);
        roomAreaMapper.deleteByRoomId(roomId);
        roomMapper.deleteById(roomId);
    }

    public Room getRoom(Long id) {
        return roomMapper.selectById(id);
    }

    public List<Room> listRooms() {
        return roomMapper.selectAll();
    }

    /** 批量保存场地座位模板（覆盖写） */
    @Transactional
    public void saveSeats(Long roomId, List<RoomSeat> seats) {
        roomSeatMapper.deleteByRoomId(roomId);
        seats.forEach(s -> s.setRoomId(roomId));
        if (!seats.isEmpty()) {
            roomSeatMapper.batchInsert(seats);
        }
    }

    public List<RoomSeat> listSeats(Long roomId) {
        return roomSeatMapper.selectByRoomId(roomId);
    }

    /** 批量保存场地默认价格区域（覆盖写） */
    @Transactional
    public void saveAreas(Long roomId, List<RoomArea> areas) {
        roomAreaMapper.deleteByRoomId(roomId);
        areas.forEach(a -> a.setRoomId(roomId));
        if (!areas.isEmpty()) {
            roomAreaMapper.batchInsert(areas);
        }
    }

    public List<RoomArea> listAreas(Long roomId) {
        return roomAreaMapper.selectByRoomId(roomId);
    }

    /**
     * 聚合查询场地模板：基本信息 + 座位 + 价格区域，
     * 前端拿到后按 areaId 在内存做 join 渲染座位图
     */
    public RoomTemplateVO getRoomTemplate(Long roomId) {
        RoomTemplateVO vo = new RoomTemplateVO();
        vo.setRoom(roomMapper.selectById(roomId));
        vo.setSeats(roomSeatMapper.selectByRoomId(roomId));
        vo.setAreas(roomAreaMapper.selectByRoomId(roomId));
        return vo;
    }

    /**
     * 将场地模板复制到场次：
     * room_seat → seat（绑定 sessionId，情侣座二次修正 pairSeatId）
     * room_area → seat_area（绑定 sessionId，作为默认价格，管理员可后续覆盖）
     *
     * @return 实际复制的座位数（调用方用来回填 show_session.total_seats）
     */
    @Transactional
    public int copyToSession(Long roomId, Long sessionId) {
        List<RoomSeat> roomSeats = roomSeatMapper.selectByRoomId(roomId);
        int seatCount = roomSeats.size();
        if (!roomSeats.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();

            // room_seat.id → Seat 对象，用于二次映射情侣座
            Map<Long, Seat> roomSeatIdMap = new HashMap<>();
            List<Seat> seats = new ArrayList<>();
            for (RoomSeat rs : roomSeats) {
                Seat s = new Seat();
                s.setSessionId(sessionId);
                s.setRowNo(rs.getRowNo());
                s.setColNo(rs.getColNo());
                s.setType(rs.getType());
                s.setAreaId(rs.getAreaId());
                s.setSeatName(rs.getSeatName());
                s.setStatus(0);
                s.setCreateTime(now);
                seats.add(s);
                roomSeatIdMap.put(rs.getId(), s);
            }

            // 第一次插入：拿到数据库生成的 seat.id
            seatMapper.batchInsert(seats);

            // 第二次：修正情侣座的 pairSeatId（room_seat.id → 新 seat.id）
            List<Seat> coupleSeats = new ArrayList<>();
            for (RoomSeat rs : roomSeats) {
                if ((rs.getType() == 2 || rs.getType() == 3) && rs.getPairSeatId() != null) {
                    Seat paired = roomSeatIdMap.get(rs.getPairSeatId());
                    if (paired != null && paired.getId() != null) {
                        Seat self = roomSeatIdMap.get(rs.getId());
                        self.setPairSeatId(paired.getId());
                        coupleSeats.add(self);
                    }
                }
            }
            if (!coupleSeats.isEmpty()) {
                seatMapper.batchUpdatePairSeatId(coupleSeats);
            }
        }

        List<RoomArea> roomAreas = roomAreaMapper.selectByRoomId(roomId);
        if (!roomAreas.isEmpty()) {
            List<SeatArea> seatAreas = roomAreas.stream().map(ra -> {
                SeatArea sa = new SeatArea();
                sa.setSessionId(sessionId);
                sa.setAreaId(ra.getAreaId());
                sa.setPrice(ra.getDefaultPrice());
                sa.setOriginPrice(ra.getDefaultOriginPrice());
                return sa;
            }).collect(Collectors.toList());
            seatAreaMapper.batchInsert(seatAreas);
        }
        return seatCount;
    }
}
