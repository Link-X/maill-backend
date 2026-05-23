package com.ticket.admin.controller;

import com.ticket.admin.dto.AdminLoginRequest;
import com.ticket.admin.dto.AdminRegisterRequest;
import com.ticket.admin.service.AdminAuthService;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员注册 / 登录 / 当前用户信息.
 *
 * 注册:需要持有 admin.invite-code(运维通过环境变量注入)才能创建管理员账号,
 *      避免任意注册成 ADMIN。事务逻辑在 AdminAuthService 中。
 * 登录:校验密码 + user_role 中含 ADMIN,签发带 roles 的 JWT。
 */
@Slf4j
@Tag(name = "管理员鉴权", description = "管理员账号的注册、登录、登出。注册需邀请码（运维通过 ADMIN_INVITE_CODE 环境变量注入）；登录成功返回 JWT，所有 /api/admin/** 接口需在 Header 携带 Bearer token")
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final String ROLE_ADMIN = AdminAuthService.ROLE_ADMIN;

    private final AdminAuthService adminAuthService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final byte[] inviteCodeHash;

    public AdminAuthController(AdminAuthService adminAuthService,
                               UserMapper userMapper,
                               UserRoleMapper userRoleMapper,
                               PasswordEncoder passwordEncoder,
                               JwtTokenProvider jwtTokenProvider,
                               TokenBlacklistService tokenBlacklistService,
                               @Value("${admin.invite-code:}") String inviteCode) {
        if (inviteCode == null || inviteCode.length() < 16) {
            throw new IllegalStateException(
                    "admin.invite-code 未配置或长度不足 16 字符,启动失败。请通过 ADMIN_INVITE_CODE 环境变量注入");
        }
        this.adminAuthService = adminAuthService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        // 预先 hash 邀请码,使比较时两侧长度固定相同,避免 length-oracle 时序攻击
        this.inviteCodeHash = sha256(inviteCode);
    }

    @Operation(summary = "管理员注册", description = "需要持有 ADMIN_INVITE_CODE 环境变量配置的邀请码。注册成功直接返回 JWT，无需再调登录。")
    @SecurityRequirements({})
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody AdminRegisterRequest req,
                                                HttpServletRequest httpReq) {
        if (!constantTimeEquals(sha256(req.getInviteCode()), inviteCodeHash)) {
            log.warn("[ADMIN-AUTH] 注册失败:邀请码错误 username={} ip={}",
                    req.getUsername(), clientIp(httpReq));
            throw new BusinessException(ErrorCode.FORBIDDEN, "邀请码错误");
        }
        User user = adminAuthService.registerAdmin(req);
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), List.of(ROLE_ADMIN));
        log.info("[ADMIN-AUTH] 管理员注册成功 userId={} username={} ip={}",
                user.getId(), user.getUsername(), clientIp(httpReq));
        return Result.success(Map.of("token", token, "userId", user.getId(), "roles", List.of(ROLE_ADMIN)));
    }

    @Operation(summary = "管理员登录", description = "用户名+密码校验，且 user_role 中必须含 ADMIN。返回的 token 粘到 Swagger UI 右上角 Authorize 按钮即可调用其他管理端接口。")
    @SecurityRequirements({})
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest req,
                                             HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        User user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("[ADMIN-AUTH] 登录失败:用户名或密码错误 username={} ip={}", req.getUsername(), ip);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("[ADMIN-AUTH] 登录失败:账号已禁用 userId={} username={} ip={}",
                    user.getId(), req.getUsername(), ip);
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        List<String> roles = userRoleMapper.selectByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());
        if (!roles.contains(ROLE_ADMIN)) {
            // 不暴露"非管理员"以减少账号枚举信息
            log.warn("[ADMIN-AUTH] 登录失败:非管理员账号尝试登录 userId={} username={} ip={}",
                    user.getId(), req.getUsername(), ip);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        log.info("[ADMIN-AUTH] 管理员登录成功 userId={} username={} ip={}",
                user.getId(), req.getUsername(), ip);
        return Result.success(Map.of("token", token, "userId", user.getId(), "roles", roles));
    }

    /**
     * 登出:把当前 token 加入 Redis 黑名单。需要携带有效 token。
     * 拦截器对此接口要求 ROLE_ADMIN,正确登录的管理员才能登出。
     */
    @Operation(summary = "管理员登出", description = "把当前 token 的 jti 加入 Redis 黑名单，剩余有效期内该 token 即失效。需携带有效 token。")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpReq) {
        String header = httpReq.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 token");
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Result.success(null);
        }
        String jti = jwtTokenProvider.getJtiFromToken(token);
        long ttl = jwtTokenProvider.getExpirationMs(token) - System.currentTimeMillis();
        tokenBlacklistService.revoke(jti, ttl);
        log.info("[ADMIN-AUTH] 管理员登出 jti={}", jti);
        return Result.success(null);
    }

    /** 固定长度的常量时间比较,避免 length-oracle */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String clientIp(HttpServletRequest req) {
        // 简单取 RemoteAddr,代理头处理由 RateLimitAspect 统一处理
        return req != null ? req.getRemoteAddr() : "unknown";
    }
}
