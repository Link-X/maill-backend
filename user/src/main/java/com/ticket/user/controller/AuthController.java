package com.ticket.user.controller;

import com.ticket.common.annotation.LimitType;
import com.ticket.common.annotation.RateLimit;
import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.result.Result;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import com.ticket.user.config.NoLogin;
import com.ticket.user.dto.LoginRequest;
import com.ticket.user.dto.RegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SnowflakeIdGenerator snowflake;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    public AuthController(UserMapper userMapper,
                          UserRoleMapper userRoleMapper,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          TokenBlacklistService tokenBlacklistService,
                          SnowflakeIdGenerator snowflake) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.snowflake = snowflake;
    }

    @NoLogin
    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
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
        role.setRole("USER");
        userRoleMapper.insert(role);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), List.of("USER"));
        return Result.success(Map.of("token", token, "userId", user.getId()));
    }

    @NoLogin
    @RateLimit(type = LimitType.BLACKLIST)
    @RateLimit(type = LimitType.IP,     limit = 30,  window = 60, message = "IP 请求过于频繁，请稍后再试")
    @RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙，请稍后重试")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                             HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        User user = userMapper.selectByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("[AUTH] 登录失败:用户名或密码错误 username={} ip={}", req.getUsername(), ip);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        List<String> roles = userRoleMapper.selectByUserId(user.getId()).stream()
                .map(UserRole::getRole)
                .collect(Collectors.toList());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        log.info("[AUTH] 登录成功 userId={} username={} ip={}", user.getId(), req.getUsername(), ip);
        return Result.success(Map.of("token", token, "userId", user.getId(), "roles", roles));
    }

    /**
     * 登出:把当前 token 加入 Redis 黑名单,剩余有效期内此 token 不再可用。
     * 需要携带有效 token 才能调用。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpReq) {
        String header = httpReq.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 token");
        }
        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return Result.success(null); // 已无效 token,直接返回成功
        }
        String jti = jwtTokenProvider.getJtiFromToken(token);
        long ttl = jwtTokenProvider.getExpirationMs(token) - System.currentTimeMillis();
        tokenBlacklistService.revoke(jti, ttl);
        log.info("[AUTH] 用户登出 jti={}", jti);
        return Result.success(null);
    }

    private String clientIp(HttpServletRequest req) {
        return req != null ? req.getRemoteAddr() : "unknown";
    }
}
