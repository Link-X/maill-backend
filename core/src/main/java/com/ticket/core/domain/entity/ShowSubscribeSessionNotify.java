package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅 × 场次推送状态。
 * 联合主键 (subscribe_id, session_id),按场次粒度保证开售提醒不重复推送。
 */
@Schema(description = "订阅×场次推送跟踪")
@Data
public class ShowSubscribeSessionNotify {
    @Schema(description = "订阅 ID") private Long subscribeId;
    @Schema(description = "场次 ID") private Long sessionId;
    @Schema(description = "已推送提前提醒 0/1") private Integer notifiedPre;
    @Schema(description = "已推送开售提醒 0/1") private Integer notifiedOpen;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
