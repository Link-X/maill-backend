package com.ticket.admin.service;

import com.ticket.admin.dto.AdminRegisterRequest;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.common.util.SnowflakeIdGenerator;
import com.ticket.core.domain.entity.User;
import com.ticket.core.domain.entity.UserRole;
import com.ticket.core.mapper.UserMapper;
import com.ticket.core.mapper.UserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Admin 认证业务层:把事务/数据库写入逻辑从 Controller 抽离,
 * 保证 @Transactional 在标准 Service Bean 上生效。
 */
@Service
public class AdminAuthService {

    public static final String ROLE_ADMIN = "ADMIN";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflake;

    public AdminAuthService(UserMapper userMapper,
                            UserRoleMapper userRoleMapper,
                            PasswordEncoder passwordEncoder,
                            SnowflakeIdGenerator snowflake) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.snowflake = snowflake;
    }

    /**
     * 注册管理员:在事务内完成 user 与 user_role 双表写入,任一失败则整体回滚.
     */
    @Transactional(rollbackFor = Exception.class)
    public User registerAdmin(AdminRegisterRequest req) {
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
        return user;
    }
}
