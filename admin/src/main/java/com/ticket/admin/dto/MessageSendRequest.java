package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "单发/批量发送消息请求")
@Data
public class MessageSendRequest {
    @NotEmpty private List<Long> userIds;
    @NotNull @Min(1) @Max(5) private Integer type;
    @NotBlank @Size(max = 200) private String title;
    @NotBlank private String content;
    @Min(0) @Max(5) private Integer linkType;
    @Size(max = 500) private String linkTarget;
}
