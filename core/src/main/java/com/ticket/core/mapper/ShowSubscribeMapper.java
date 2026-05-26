package com.ticket.core.mapper;
import com.ticket.core.domain.entity.ShowSubscribe;
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

    /** 定时:扫描需要"提前提醒"的订阅 */
    List<ShowSubscribe> selectPendingPre(@Param("now") LocalDateTime now);

    /** 定时:扫描需要"开售"的订阅 */
    List<ShowSubscribe> selectPendingOpen(@Param("now") LocalDateTime now);

    /** 标记已推送 */
    int markNotifiedPre(@Param("id") Long id);
    int markNotifiedOpen(@Param("id") Long id);
}
