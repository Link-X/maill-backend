package com.ticket.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "艺人上下架请求")
@Data
public class ArtistStatusRequest {
    @NotNull private Long id;
    @NotNull @Min(0) @Max(1) private Integer status;
}
