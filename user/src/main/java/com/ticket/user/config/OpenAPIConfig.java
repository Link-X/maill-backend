package com.ticket.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * User 端 OpenAPI（Swagger UI）全局配置。
 * 默认全接口需 Bearer JWT；带 @NoLogin 注解的免登录接口在文档里也仍会显示"需要 Authorize"，
 * 实际后端 LoginCheckInterceptor 会放行 —— 这是 Swagger 层面的提示，不影响功能。
 */
@Configuration
public class OpenAPIConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI userOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticket User API")
                        .description("抢票系统用户端接口文档。带 @NoLogin 的接口（首页浏览、注册登录、核销等）无需 token；"
                                + "其它接口需 Bearer JWT。响应统一为 Result&lt;T&gt; { code, message, data, traceId }。")
                        .version("1.0.0")
                        .contact(new Contact().name("Ticket Backend Team")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("/api/auth/login 返回的 token 粘到这里")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
