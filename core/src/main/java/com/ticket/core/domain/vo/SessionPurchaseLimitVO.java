package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户在某场次的限购状态 — 前端用以限制选座/派座的可下单数量。
 *
 * <p>计算逻辑(已与下单链路对齐):
 * <ul>
 *   <li>{@code limitPerUser}:场次配置的每用户限购张数(`show_session.limit_per_user`)</li>
 *   <li>{@code purchased}:Redis `session:purchase:{sessionId}:{userId}` 当前值,包含已锁未支付 + 已支付</li>
 *   <li>{@code remaining = max(0, limitPerUser - purchased)}</li>
 * </ul>
 *
 * <p>派座区情侣对的"对数上限" = {@code remaining / 2}(向下取整),因为一对算 2 张。
 */
@Schema(description = "用户在某场次的限购状态")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionPurchaseLimitVO {
    @Schema(description = "场次配置的每用户限购张数", example = "4")
    private Integer limitPerUser;

    @Schema(description = "当前已购张数(含未支付锁定 + 已支付)", example = "2")
    private Integer purchased;

    @Schema(description = "剩余可购张数 = max(0, limitPerUser - purchased)", example = "2")
    private Integer remaining;
}
