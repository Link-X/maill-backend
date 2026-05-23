package com.ticket.core.domain.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "订单项；一个订单的每个座位对应一条")
@Data
public class OrderItem {
    @Schema(description = "ID", example = "1") private Long id;
    @Schema(description = "关联订单 ID", example = "1") private Long orderId;
    @Schema(description = "座位 ID", example = "101") private Long seatId;
    @Schema(description = "成交价格", example = "880.00") private BigDecimal price;
    @Schema(description = "座位信息字符串（如 '1排01座'）", example = "1排01座") private String seatInfo;
}
