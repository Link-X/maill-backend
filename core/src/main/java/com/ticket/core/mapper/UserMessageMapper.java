package com.ticket.core.mapper;
import com.ticket.core.domain.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMessageMapper {
    int insert(UserMessage um);
    int batchInsert(@Param("list") List<UserMessage> list);
    /** 懒生成:为 userId 对所有还没有关系记录的广播消息批量 INSERT */
    int lazyInsertBroadcastsFor(@Param("userId") Long userId);

    int markRead(@Param("ids") List<Long> ids, @Param("userId") Long userId, @Param("now") LocalDateTime now);
    int markAllRead(@Param("userId") Long userId, @Param("type") Integer type, @Param("now") LocalDateTime now);
    int deleteByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    /** user 端列表 join Message,按 create_time DESC */
    List<UserMessage> selectByUser(@Param("userId") Long userId,
                                   @Param("type") Integer type,
                                   @Param("offset") Integer offset,
                                   @Param("size") Integer size);

    int countUnreadByType(@Param("userId") Long userId, @Param("type") Integer type);
    int countUnreadAll(@Param("userId") Long userId);
}
