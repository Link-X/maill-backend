package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派座任务实体
 * 派座流程「同步扣 Redis 库存 → 异步派具体座位 → 建单」的中间状态持久化载体。
 */
@Schema(description = "派座任务")
@Data
public class SeatAllocationTask {
    @Schema(description = "主键") private Long id;
    @Schema(description = "订单号(submit 同步预生成)") private String orderNo;
    @Schema(description = "用户ID") private Long userId;
    @Schema(description = "场次ID") private Long sessionId;
    @Schema(description = "区域ID") private String areaId;

    @Schema(description = "票种: 1=单座, 2=情侣对", allowableValues = {"1", "2"})
    private Integer ticketType;

    @Schema(description = "ticket_type=1 时为张数, =2 时为对数") private Integer quantity;

    @Schema(description = "0=待派, 1=成功, 2=失败, 3=已回滚", allowableValues = {"0", "1", "2", "3"})
    private Integer status;

    @Schema(description = "派出的座位ID列表(逗号分隔)") private String allocatedSeats;
    @Schema(description = "失败原因") private String failReason;

    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;

    public static final int TICKET_TYPE_SINGLE = 1;
    public static final int TICKET_TYPE_COUPLE = 2;

    public static final int STATUS_PENDING    = 0;
    public static final int STATUS_SUCCESS    = 1;
    public static final int STATUS_FAILED     = 2;
    public static final int STATUS_ROLLED_BACK = 3;
}
