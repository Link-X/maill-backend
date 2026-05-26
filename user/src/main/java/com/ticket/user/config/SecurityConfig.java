package com.ticket.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.auth.JwtAuthenticationFilter;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import com.ticket.common.result.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

/**
 * Spring Security 配置：无状态 JWT 认证 + 白名单模式。
 *
 * 改造说明:
 * - 旧版 .anyRequest().permitAll() 完全放行,鉴权完全依赖 LoginCheckInterceptor。
 *   一旦新接口忘记加 @NoLogin 又忘记在拦截器中处理就会有反向漏洞。
 * - 新版改为"显式 permitAll 白名单 + 其余 authenticated()"。
 *   Security 层做兜底防护,Interceptor 继续保留 @NoLogin 业务语义。
 *   两层防护:新接口忘记加 @NoLogin 但不在白名单 → 直接被 Security 拒,不会裸露。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider,
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
            .exceptionHandling().authenticationEntryPoint(jsonEntryPoint()).and()
            .authorizeHttpRequests(auth -> auth
                // 文档 / 错误页
                .antMatchers("/error",
                        "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**",
                        "/swagger-resources/**", "/webjars/**").permitAll()
                // 鉴权(注册/登录/登出 — 登出虽要 token,但走 permitAll 由业务自身校验)
                .antMatchers("/api/auth/**").permitAll()
                // 公开的浏览/搜索类接口(对应 @NoLogin 类级)
                .antMatchers("/api/show/**",
                        "/api/article/**",
                        "/api/article-category/**",
                        "/api/search/**",
                        "/api/category/**",
                        "/api/city/**",
                        "/api/session/**",
                        "/api/banner/**").permitAll()
                // 方法级 @NoLogin 的接口:艺人列表/详情
                .antMatchers(HttpMethod.GET, "/api/artist/list", "/api/artist/*").permitAll()
                // 方法级 @NoLogin 的接口:评价列表/回复/详情
                .antMatchers(HttpMethod.GET,
                        "/api/review/list",
                        "/api/review/replies",
                        "/api/review/detail/**").permitAll()
                // 其余接口:必须已认证
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    /** Security 拒绝未认证请求时返回 JSON,而非默认 HTML/空 body */
    private AuthenticationEntryPoint jsonEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Result.fail(401, "未登录，请先登录")));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
