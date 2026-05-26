package com.ticket.core.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅推送扫描结果(一行 = 一个订阅 × 一个待推场次)。
 *
 * 由 {@code ShowSubscribeMapper} 扫描出,{@code SubscribeNotifier} 按 (subscribeId, date(openSaleTime)) 分组,
 * 把同一演出同一天开售的多个场次合并为一条消息推送,最后批量写 {@code show_subscribe_session_notify} 标记已通知。
 */
@Data
public class PendingSubscribeNotify {
    private Long subscribeId;
    private Long userId;
    private Long showId;
    private String showName;
    private Long sessionId;
    private String sessionName;
    private LocalDateTime sessionStartTime;
    private LocalDateTime openSaleTime;
    private Integer notifyBeforeMinutes;
}
