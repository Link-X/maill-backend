package com.ticket.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单状态响应：含订单本身 + 演出/场次/城市/票券完整信息")
@Data
public class OrderStatusResponse {

    @Schema(description = "票券信息")
    @Data
    public static class TicketInfo {
        @Schema(description = "票券编号（8 位友好票号）", example = "GH37KX2P") private String ticketNo;
        @Schema(description = "入场二维码 UUID") private String qrCode;
        @Schema(description = "票券状态 0=未使用 1=已核销 2=已作废（退款）", example = "0", allowableValues = {"0","1","2"})
        private Integer status;
        @Schema(description = "核销时间，status=1 时有值") private LocalDateTime verifyTime;
    }

    @Schema(description = "订单 ID", example = "1") private Long orderId;
    @Schema(description = "订单号（雪花 ID）", example = "704179544755671040") private String orderNo;
    @Schema(description = "订单状态 0=待支付 1=已支付 2=已取消 3=退款中 4=已退款 5=部分退款",
            example = "1", allowableValues = {"0","1","2","3","4","5"}) private Integer status;
    @Schema(description = "订单总金额", example = "780.00") private BigDecimal totalAmount;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "支付时间，status>=1 时有值") private LocalDateTime payTime;
    @Schema(description = "过期时间，status=0 时前端可据此显示支付倒计时") private LocalDateTime expireTime;
    @Schema(description = "座位信息字符串列表", example = "[\"1排01座\", \"1排02座\"]") private List<String> seatInfos;
    @Schema(description = "票券列表（支付成功后异步生成）") private List<TicketInfo> tickets;

    @Schema(description = "演出名称", example = "周杰伦嘉年华世界巡回演唱会") private String showName;
    @Schema(description = "演出场馆", example = "上海梅赛德斯奔驰文化中心") private String showVenue;
    @Schema(description = "演出城市代码", example = "310000") private String showCityCode;
    @Schema(description = "演出城市名（冗余）", example = "上海") private String showCityName;
    @Schema(description = "演出详细地址", example = "浦东新区世博大道 1200 号") private String showAddress;
    @Schema(description = "场次名称", example = "上海 2026-06-01") private String sessionName;
    @Schema(description = "场次开始时间", example = "2026-06-01T19:00:00") private LocalDateTime sessionStartTime;
}
