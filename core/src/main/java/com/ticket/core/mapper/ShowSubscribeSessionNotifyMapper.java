package com.ticket.core.mapper;

import com.ticket.core.domain.vo.SubscribeSessionRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订阅×场次推送状态 mapper。
 * 用 INSERT ... ON DUPLICATE KEY UPDATE 实现批量幂等写。
 */
@Mapper
public interface ShowSubscribeSessionNotifyMapper {

    /** 批量把传入的 (subscribeId, sessionId) 标为已推送提前提醒 */
    int batchUpsertNotifiedPre(@Param("pairs") List<SubscribeSessionRef> pairs);

    /** 批量把传入的 (subscribeId, sessionId) 标为已推送开售提醒 */
    int batchUpsertNotifiedOpen(@Param("pairs") List<SubscribeSessionRef> pairs);
}
