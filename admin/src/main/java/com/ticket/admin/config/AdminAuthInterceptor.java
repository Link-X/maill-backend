package com.ticket.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Admin 接口认证拦截器：要求请求头 X-Admin-Token 与配置 admin.api-key 一致.
 *
 * 当前项目未建立完整的管理员账号体系，临时通过静态 API Key 封堵未授权访问。
 * 部署时务必通过环境变量 ADMIN_API_KEY 注入强随机值（≥32 字符）。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final String apiKey;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(@Value("${admin.api-key:}") String apiKey,
                                ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.length() < 16) {
            throw new IllegalStateException(
                    "admin.api-key 未配置或长度不足 16 字符，启动失败。请通过 ADMIN_API_KEY 环境变量注入");
        }
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String token = request.getHeader("X-Admin-Token");
        if (token == null || !constantTimeEquals(token, apiKey)) {
            writeUnauthorized(response);
            return false;
        }
        return true;
    }

    /** 常量时间字符串比较，防止时序攻击 */
    private boolean constantTimeEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(401, "未授权访问")));
    }
}
