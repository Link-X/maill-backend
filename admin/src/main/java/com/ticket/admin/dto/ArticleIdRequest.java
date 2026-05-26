package com.ticket.admin.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ArticleIdRequest {
    @NotNull public Long id;
}
