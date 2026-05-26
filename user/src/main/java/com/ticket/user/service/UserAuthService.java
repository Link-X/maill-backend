package com.ticket.user.service;

import com.ticket.common.auth.JwtTokenProvider;
import com.ticket.common.auth.TokenBlacklistService;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.domain.vo.LoginResultVO;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import com.ticket.user.dto.LoginRequest;
import com.ticket.user.dto.RegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户鉴权服务：注册 / 登录 / 登出 的业务逻辑层。
 *
 * 从 AuthController 抽取，使 Controller 仅负责协议适配，
 * 业务规则（用户名唯一、角色分配、密码哈希、JWT 生成）下沉到 Service。
 */
@Slf4j
@Service
public class UserAuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final SnowflakeIdGenerator snowflake;

    public UserAuthService(UserMapper userMapper,
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

    /**
     * 注册：user + user_role 写在同一事务中，避免角色丢失。
     */
    @Transactional
    public LoginResultVO register(RegisterRequest req) {
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
        role.setRole(DEFAULT_ROLE);
        userRoleMapper.insert(role);

        List<String> roles = List.of(DEFAULT_ROLE);
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);
        return LoginResultVO.of(token, user.getId(), roles);
    }

    public LoginResultVO login(LoginRequest req, String ip) {
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
        return LoginResultVO.of(token, user.getId(), roles);
    }

    /**
     * 登出：把 token 的 jti 加入 Redis 黑名单。
     * @param token 已通过 "Bearer " 前缀剥离的纯 token
     */
    public void logout(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            return; // 已无效 token，幂等返回
        }
        String jti = jwtTokenProvider.getJtiFromToken(token);
        long ttl = jwtTokenProvider.getExpirationMs(token) - System.currentTimeMillis();
        tokenBlacklistService.revoke(jti, ttl);
        log.info("[AUTH] 用户登出 jti={}", jti);
    }
}
