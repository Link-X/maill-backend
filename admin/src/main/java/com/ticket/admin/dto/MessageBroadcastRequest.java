package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Schema(description = "群发消息请求")
@Data
public class MessageBroadcastRequest {
    @NotNull @Min(1) @Max(5) private Integer type;
    @NotBlank @Size(max = 200) private String title;
    @NotBlank private String content;
    @Min(0) @Max(5) private Integer linkType;
    @Size(max = 500) private String linkTarget;
}
