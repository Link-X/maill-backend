package com.ticket.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/**
 * payment 模块无 Spring Security,通过 WebMvcConfigurer 启用 CORS。
 * 复用 common 中的 CorsConfigurationSource Bean 保持配置一致。
 */
@Configuration
public class PaymentWebMvcConfig implements WebMvcConfigurer {

    private final UrlBasedCorsConfigurationSource corsConfigurationSource;

    public PaymentWebMvcConfig(UrlBasedCorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 把 common 中已构造好的 CorsConfiguration 注册到所有路径
        Map<String, org.springframework.web.cors.CorsConfiguration> configs =
                corsConfigurationSource.getCorsConfigurations();
        configs.forEach((path, cfg) -> {
            registry.addMapping(path)
                    .allowedOriginPatterns(cfg.getAllowedOriginPatterns().toArray(new String[0]))
                    .allowedMethods(cfg.getAllowedMethods().toArray(new String[0]))
                    .allowedHeaders(cfg.getAllowedHeaders().toArray(new String[0]))
                    .exposedHeaders(cfg.getExposedHeaders().toArray(new String[0]))
                    .allowCredentials(Boolean.TRUE.equals(cfg.getAllowCredentials()))
                    .maxAge(cfg.getMaxAge() != null ? cfg.getMaxAge() : 3600L);
        });
    }
}
