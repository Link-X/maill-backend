package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "场次座位实体（DB 表）；实时库存由 Redis 维护，DB.status 在支付后异步同步")
@Data
public class Seat {
    @Schema(description = "座位 ID", example = "101") private Long id;
    @Schema(description = "场次 ID", example = "1") private Long sessionId;
    @Schema(description = "排号", example = "1") private Integer rowNo;
    @Schema(description = "列号", example = "1") private Integer colNo;
    @Schema(description = "座位类型 1=普通 2=情侣左 3=情侣右", example = "1", allowableValues = {"1","2","3"}) private Integer type;
    @Schema(description = "价格区域 ID", example = "1") private String areaId;
    @Schema(description = "座位名称", example = "1排01座") private String seatName;
    @Schema(description = "情侣连座配对座位 ID，type=2/3 才有值") private Long pairSeatId;
    @Schema(description = "状态 0=可售 1=已锁 2=已售（由支付后异步同步）", example = "0", allowableValues = {"0","1","2"}) private Integer status;
    @Schema(description = "创建时间") private LocalDateTime createTime;
}
