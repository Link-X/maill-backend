package com.ticket.core.mapper;
import com.ticket.core.domain.entity.ShowSubscribe;
import com.ticket.core.domain.vo.PendingSubscribeNotify;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShowSubscribeMapper {
    /** INSERT IGNORE 幂等 */
    int insertIgnore(@Param("userId") Long userId,
                     @Param("showId") Long showId,
                     @Param("notifyBeforeMinutes") Integer notifyBeforeMinutes);

    int delete(@Param("userId") Long userId, @Param("showId") Long showId);

    int countSubscribe(@Param("userId") Long userId, @Param("showId") Long showId);

    /** user 我的订阅列表 join show */
    List<ShowSubscribe> selectByUser(@Param("userId") Long userId,
                                     @Param("offset") Integer offset,
                                     @Param("size") Integer size);

    int countByUser(@Param("userId") Long userId);

    /**
     * 扫描"提前提醒"待推送项:演出每个场次单独算一行,过滤掉已推送的场次。
     * 返回的多行可能属于同一订阅,SubscribeNotifier 按 (subscribeId, 开售日) 合并发送。
     */
    List<PendingSubscribeNotify> selectPendingPreBySession(@Param("now") LocalDateTime now);

    /**
     * 扫描"开售"待推送项:开售时间已到、24h 内、未推送的场次。
     * 限制 24h 窗口避免历史订阅在场次开售已久后突然补发。
     */
    List<PendingSubscribeNotify> selectPendingOpenBySession(@Param("now") LocalDateTime now);
}
