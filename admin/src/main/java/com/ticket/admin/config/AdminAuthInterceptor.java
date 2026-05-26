package com.ticket.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.result.Result;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Admin 接口认证拦截器:要求 JWT 已通过 JwtAuthenticationFilter 校验,且 token 中含 ROLE_ADMIN.
 *
 * 注册/登录接口(/api/admin/auth/**)在 AdminWebMvcConfig 中已 excludePathPatterns 排除。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            writeError(response, 401, "未登录,请先登录");
            return false;
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
        if (!isAdmin) {
            writeError(response, 403, "无管理员权限");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, msg)));
    }
}
