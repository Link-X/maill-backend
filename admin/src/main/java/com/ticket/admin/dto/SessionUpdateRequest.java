package com.ticket.admin.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 更新场次
 * - 不允许更新 status（开售走 /publish 接口）
 * - 不允许更新 totalSeats / rowCount / colCount（后端根据 room 模板维护）
 * - 不允许更换 showId / roomId（这种业务变更应该删除重建）
 */
@Data
public class SessionUpdateRequest {

    @NotNull(message = "id 不能为空")
    private Long id;

    @NotBlank(message = "场次名称不能为空")
    @Size(max = 128, message = "场次名称最长 128 字符")
    private String name;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @Min(value = 1, message = "limitPerUser 最小 1")
    private Integer limitPerUser;

    private Map<String, Object> extend;
}
