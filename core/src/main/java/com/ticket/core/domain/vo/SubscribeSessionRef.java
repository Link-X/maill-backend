package com.ticket.core.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订阅×场次的最小引用。专为 ShowSubscribeSessionNotifyMapper 的批量 upsert 服务,
 * 用具名字段避免 MyBatis 在 <foreach> 中解析原生数组索引(#{p[0]})的不确定性。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeSessionRef {
    private Long subscribeId;
    private Long sessionId;
}
