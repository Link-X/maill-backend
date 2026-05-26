package com.ticket.core.domain.entity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "用户消息关系(含懒生成的广播副本)")
@Data
public class UserMessage {
    @Schema(description = "关系 ID") private Long id;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "消息 ID") private Long messageId;
    @Schema(description = "0=未读 1=已读") private Integer isRead;
    @Schema(description = "已读时间") private LocalDateTime readAt;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
    /** join 注入 */
    @Schema(description = "消息内容(join)") private Message message;
}
