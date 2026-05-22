package com.ticket.admin.controller;

import com.ticket.admin.dto.AdminLoginRequest;
import com.ticket.admin.dto.AdminRegisterRequest;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员注册 / 登录 / 当前用户信息.
 *
 * 注册:需要持有 admin.invite-code(运维通过环境变量注入)才能创建管理员账号,
 *      避免任意注册成 ADMIN。
 * 登录:校验密码 + user_role 中含 ADMIN,签发带 roles 的 JWT。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SnowflakeIdGenerator snowflake;
    private final String inviteCode;

    public AdminAuthController(UserMapper userMapper,
                               UserRoleMapper userRoleMapper,
                               PasswordEncoder passwordEncoder,
                               JwtTokenProvider jwtTokenProvider,
                               SnowflakeIdGenerator snowflake,
                               @Value("${admin.invite-code:}") String inviteCode) {
        if (inviteCode == null || inviteCode.length() < 16) {
            throw new IllegalStateException(
                    "admin.invite-code 未配置或长度不足 16 字符,启动失败。请通过 ADMIN_INVITE_CODE 环境变量注入");
        }
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.snowflake = snowflake;
        this.inviteCode = inviteCode;
    }

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> register(@Valid @RequestBody AdminRegisterRequest req) {
        if (!constantTimeEquals(req.getInviteCode(), inviteCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "邀请码错误");
        }
        if (userMapper.selectByUsername(req.getUsername()) != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setId(snowflake.nextId());
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setStatus(1);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        UserRole role = new UserRole();
        role.setUserId(user.getId());
        role.setRole(ROLE_ADMIN);
        userRoleMapper.insert(role);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), List.of(ROLE_ADMIN));
        return Result.success(Map.of("token", token, "userId", user.getId(), "roles", List.of(ROLE_ADMIN)));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AdminLoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }
        List<String> roles = userRoleMapper.selectByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());
        if (!roles.contains(ROLE_ADMIN)) {
            // 不暴露"非管理员"以减少账号枚举信息
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        return Result.success(Map.of("token", token, "userId", user.getId(), "roles", roles));
    }

    /** 常量时间比较,防止时序攻击 */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
