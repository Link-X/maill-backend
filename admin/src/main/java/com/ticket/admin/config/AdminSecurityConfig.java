package com.ticket.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.auth.JwtAuthenticationFilter;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import com.ticket.common.result.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

/**
 * Admin 模块 Security 配置:无状态 JWT 认证 + 白名单模式。
 *
 * 改造说明:
 * - 旧版 .anyRequest().permitAll() 完全放行,鉴权完全依赖 AdminAuthInterceptor。
 *   存在反向漏洞:新接口忘记 excludePath 反而暴露。
 * - 新版 Security 层强制 /api/admin/** 必须 ROLE_ADMIN,/api/admin/auth/** 白名单。
 *   AdminAuthInterceptor 继续做二次校验(便于返回友好 JSON 错误)。
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    private static final String ROLE_ADMIN = "ADMIN";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public AdminSecurityConfig(JwtTokenProvider jwtTokenProvider,
                               TokenBlacklistService tokenBlacklistService,
                               ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .exceptionHandling()
                .authenticationEntryPoint(jsonEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler())
            .and()
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/error",
                        "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**",
                        "/swagger-resources/**", "/webjars/**",
                        "/actuator/**").permitAll()
                // admin 登录/注册:无 token 才能调
                .antMatchers("/api/admin/auth/**").permitAll()
                // 其余 admin 接口必须有 ROLE_ADMIN
                .antMatchers("/api/admin/**").hasRole(ROLE_ADMIN)
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    private AuthenticationEntryPoint jsonEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Result.fail(401, "未登录，请先登录")));
        };
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Result.fail(403, "无管理员权限")));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
