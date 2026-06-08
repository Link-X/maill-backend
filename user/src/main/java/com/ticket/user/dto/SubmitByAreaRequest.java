package com.ticket.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 派座模式下单请求。
 *
 * 用户在派座区(sale_mode=2)选择「区域 + 票种 + 数量」,由系统派具体座位。
 */
@Schema(description = "派座模式下单(区域+票种+数量)")
@Data
public class SubmitByAreaRequest {

    @Schema(description = "场次 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;

    @Schema(description = "区域 ID(场次内唯一)", example = "B", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "区域ID不能为空")
    private String areaId;

    @Schema(description = "票种: 1=单座, 2=情侣对", example = "1",
            allowableValues = {"1", "2"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "票种不能为空")
    @Min(value = 1, message = "票种取值范围 1~2")
    @Max(value = 2, message = "票种取值范围 1~2")
    private Integer ticketType;

    @Schema(description = "数量:ticketType=1 时为张数, =2 时为对数。单次最多 4(对应 4 张/4 对=8 座)",
            example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少 1")
    @Max(value = 4, message = "单次下单最多 4(防止单请求大量占用库存)")
    private Integer quantity;
}
