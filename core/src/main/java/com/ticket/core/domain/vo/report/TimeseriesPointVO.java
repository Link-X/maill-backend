package com.ticket.core.domain.vo.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Schema(description = "时间趋势图一个数据点")
@Data
public class TimeseriesPointVO {

    @Schema(description = "时间点；格式随 dim：day→2026-05-23，hour→2026-05-23T14，month→2026-05", example = "2026-05-23")
    private String date;

    @Schema(description = "该时段已支付订单数（status=1/5）", example = "24")
    private Integer orderCount;

    @Schema(description = "该时段营收", example = "12800.00")
    private BigDecimal revenue;

    @Schema(description = "该时段退款金额", example = "200.00")
    private BigDecimal refundAmount;
}
