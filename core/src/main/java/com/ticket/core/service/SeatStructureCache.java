package com.ticket.core.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticket.core.domain.entity.City;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.mapper.CityMapper;
import com.ticket.core.mapper.SeatAreaMapper;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.ShowMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 座位结构本地缓存(Caffeine)。
 *
 * 缓存目标:座位/价格区域/演出信息/城市信息——这些是静态或低频变化的数据,
 * 用于防止热门场次开售前/期间用户刷新座位图打爆 MySQL。
 *
 * 不缓存 ShowSession 的原因:
 * - 它的 status 字段会被定时任务修改,多实例本地缓存难以即时同步,
 *   缓存延迟会导致用户在不同实例看到不同的开售状态
 * - selectById 是单行主键查询,本身极轻量,无需缓存
 *
 * 失效策略:
 * - 5 分钟 TTL 兜底,确保多实例最终一致
 * - 管理员修改座位/价格时主动 invalidate
 */
@Service
public class SeatStructureCache {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final long MAX_SIZE = 10_000;

    private final Cache<Long, List<Seat>> seatsBySession =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_SIZE).build();
    private final Cache<Long, List<SeatArea>> areasBySession =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_SIZE).build();
    private final Cache<Long, Show> showById =
            Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(MAX_SIZE).build();
    private final Cache<String, City> cityByCode =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(1_000).build();

    private final SeatMapper seatMapper;
    private final SeatAreaMapper seatAreaMapper;
    private final ShowMapper showMapper;
    private final CityMapper cityMapper;

    public SeatStructureCache(SeatMapper seatMapper,
                              SeatAreaMapper seatAreaMapper,
                              ShowMapper showMapper,
                              CityMapper cityMapper) {
        this.seatMapper = seatMapper;
        this.seatAreaMapper = seatAreaMapper;
        this.showMapper = showMapper;
        this.cityMapper = cityMapper;
    }

    public List<Seat> getSeats(Long sessionId) {
        return seatsBySession.get(sessionId, seatMapper::selectBySessionId);
    }

    public List<SeatArea> getAreas(Long sessionId) {
        return areasBySession.get(sessionId, seatAreaMapper::selectBySessionId);
    }

    public Show getShow(Long showId) {
        return showById.get(showId, showMapper::selectById);
    }

    public City getCity(String cityCode) {
        return cityByCode.get(cityCode, cityMapper::selectByCode);
    }

    /** 座位结构变更(批量建/删座位)时调 */
    public void invalidateSeats(Long sessionId) {
        seatsBySession.invalidate(sessionId);
    }

    /** 价格区域变更时调 */
    public void invalidateAreas(Long sessionId) {
        areasBySession.invalidate(sessionId);
    }

    /** 演出变更 */
    public void invalidateShow(Long showId) {
        showById.invalidate(showId);
    }
}
