package com.ticket.core.domain.dto;

import lombok.Data;
import java.util.List;

/**
 * 订单创建请求 DTO
 */
@Data
public class OrderCreateRequest {
    /**
     * 预生成的雪花订单号(由 submit 接口生成,异步建单消费者用同一个 orderNo INSERT,
     * 充当幂等 key + 前端轮询凭证)。可空,空时由 service 内部生成。
     */
    private String orderNo;
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 座位ID列表
     */
    private List<Long> seatIds;
}
