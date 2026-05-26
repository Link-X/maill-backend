package com.ticket.core.mapper;
import com.ticket.core.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MessageMapper {
    int insert(Message m);
    Message selectById(@Param("id") Long id);
    /** 找出所有广播消息(用于懒生成) */
    List<Message> selectBroadcasts();
    /** admin 列表 */
    List<Message> selectByCondition(@Param("type") Integer type,
                                    @Param("broadcast") Integer broadcast,
                                    @Param("offset") Integer offset,
                                    @Param("size") Integer size);
    int countByCondition(@Param("type") Integer type, @Param("broadcast") Integer broadcast);
}
