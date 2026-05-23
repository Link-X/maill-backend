package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创建场次
 * - status 后端固定为 0（未开放），开售用 PUT /api/admin/session/{id}/publish
 * - totalSeats / rowCount / colCount 由后端根据 roomId 自动计算与回填，不接受前端传入
 */
@Data
public class SessionCreateRequest {

    @NotNull(message = "showId 不能为空")
    private Long showId;

    /** 推荐传 roomId，否则需另行调用 /seat/batch 与 /area/save */
    private Long roomId;

    @NotBlank(message = "场次名称不能为空")
    @Size(max = 128, message = "场次名称最长 128 字符")
    private String name;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    /** 每人限购数，默认 1 */
    @Min(value = 1, message = "limitPerUser 最小 1")
    private Integer limitPerUser;

    private Map<String, Object> extend;
}
