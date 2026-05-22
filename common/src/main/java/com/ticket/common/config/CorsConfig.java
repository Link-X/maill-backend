package com.ticket.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 跨域统一配置:
 *   - 允许 origin 通过 cors.allowed-origins 配置(逗号分隔),dev 默认放开本地常用端口
 *   - allowCredentials=true 时 origin 不能用 "*"
 *   - 用 allowedOriginPatterns 而非 allowedOrigins,以支持 "*" 与 credentials 共存
 *
 * 使用方:
 *   - 带 Spring Security 的模块(user/admin):SecurityFilterChain 内调用 .cors()
 *     即可自动注入此 Bean
 *   - 无 Spring Security 的模块(payment):在 WebMvcConfigurer 里走 addCorsMappings
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split("\\s*,\\s*"));
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 浏览器需要读到的响应头(如登录返回的 token 之外的扩展)
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
