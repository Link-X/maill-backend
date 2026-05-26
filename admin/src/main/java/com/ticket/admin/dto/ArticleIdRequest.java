package com.ticket.admin.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class ArticleIdRequest {
    @NotNull public Long id;
}
