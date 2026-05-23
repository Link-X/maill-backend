package com.ticket.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Admin 端 OpenAPI（Swagger UI）全局配置：
 * - 标题 / 描述 / 版本
 * - 全局 Bearer JWT 鉴权（Swagger UI 右上角 Authorize 按钮）
 */
@Configuration
public class OpenAPIConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI adminOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticket Admin API")
                        .description("抢票系统管理端接口文档。所有接口（除登录/注册）需 Bearer JWT，"
                                + "角色 = ADMIN。响应统一为 Result&lt;T&gt; { code, message, data, traceId }。")
                        .version("1.0.0")
                        .contact(new Contact().name("Ticket Backend Team")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后把 /api/admin/auth/login 返回的 token 粘到这里")))
                // 全局生效：所有 endpoint 默认要求 bearerAuth；登录/注册接口用 @SecurityRequirements({}) 关掉
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
