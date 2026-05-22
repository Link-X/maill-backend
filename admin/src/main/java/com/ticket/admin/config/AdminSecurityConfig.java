package com.ticket.admin.config;

import com.ticket.common.auth.JwtAuthenticationFilter;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Admin 模块 Security 配置:无状态 JWT 认证,角色检查由 AdminAuthInterceptor 完成
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public AdminSecurityConfig(JwtTokenProvider jwtTokenProvider,
                               TokenBlacklistService tokenBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()                                   // 启用 CORS,使用 common 中的 CorsConfigurationSource Bean
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
