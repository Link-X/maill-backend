package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "票券实体（支付成功后异步生成）")
@Data
public class Ticket {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "关联订单 ID", example = "1") private Long orderId;
    @Schema(description = "座位 ID", example = "101") private Long seatId;
    @Schema(description = "用户 ID", example = "100") private Long userId;
    @Schema(description = "入场二维码 UUID") private String qrCode;
    @Schema(description = "8 位友好票号（排除 O/0/I/1）", example = "GH37KX2P") private String ticketNo;
    @Schema(description = "票券状态 0=未使用 1=已核销 2=已作废（退款）", example = "0", allowableValues = {"0","1","2"}) private Integer status;
    @Schema(description = "核销时间，status=1 时有值") private LocalDateTime verifyTime;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
}
