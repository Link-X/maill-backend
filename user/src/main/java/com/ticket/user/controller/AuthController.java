package com.ticket.user.controller;

import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.LoginResultVO;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.LoginRequest;
import com.ticket.user.dto.RegisterRequest;
import com.ticket.user.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Slf4j
@Tag(name = "用户鉴权", description = "用户端的注册、登录、登出。登录返回 JWT，需要鉴权的接口在 Header 携带 Bearer token")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthService userAuthService;

    public AuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @Operation(summary = "用户注册", description = "用户名唯一；注册成功直接返回 JWT，无需再调登录。受 IP/全局限流保护")
    @SecurityRequirements({})
    @NoLogin
    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @PostMapping("/register")
    public Result<LoginResultVO> register(@Valid @RequestBody RegisterRequest req) {
        return Result.success(userAuthService.register(req));
    }

    @Operation(summary = "用户登录", description = "返回的 token 粘到 Swagger UI 右上角 Authorize 按钮即可调用其它接口")
    @SecurityRequirements({})
    @NoLogin
    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest httpReq) {
        return Result.success(userAuthService.login(req, clientIp(httpReq)));
    }

    @Operation(summary = "用户登出", description = "把当前 token 的 jti 加入 Redis 黑名单，剩余有效期内该 token 即失效。需携带有效 token")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpReq) {
        String header = httpReq.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 token");
        }
        userAuthService.logout(header.substring(7));
        return Result.success(null);
    }

    private String clientIp(HttpServletRequest req) {
        return req != null ? req.getRemoteAddr() : "unknown";
    }
}
