package com.sits.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.SysUser;
import com.sits.admin.mapper.SysUserMapper;
import com.sits.admin.service.SysUserService;
import cn.hutool.crypto.digest.BCrypt;
import com.sits.common.exception.BusinessException;
import com.sits.common.service.UserAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统用户管理服务实现。
 *
 * <p>同时实现 {@link UserAuthService} 接口，供 sits-common 的 AuthController
 * 和 StpInterfaceImpl 注入使用。
 */
@Service
public class SysUserServiceImpl implements SysUserService, UserAuthService {

    private static final Logger log = LoggerFactory.getLogger(SysUserServiceImpl.class);

    private final SysUserMapper sysUserMapper;

    public SysUserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    // ==================== UserAuthService 实现 ====================

    @Override
    public Long validate(String username, String rawPassword) {
        SysUser user = getByUsername(username);
        if (user == null) {
            log.warn("用户不存在: {}", username);
            return null;
        }
        if ("DISABLED".equals(user.getStatus())) {
            log.warn("用户已禁用: {}", username);
            return null;
        }
        // BCrypt 校验密码（使用 Hutool）
        if (!BCrypt.checkpw(rawPassword, user.getPassword())) {
            log.warn("密码错误: {}", username);
            return null;
        }
        log.info("用户登录成功: {}", username);
        return user.getId();
    }

    @Override
    public List<String> getRolesByUserId(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return new ArrayList<>();
        }
        // 返回单个角色编码（当前设计每个用户只有一个角色）
        List<String> roles = new ArrayList<>();
        roles.add(user.getRoleCode());
        return roles;
    }

    @Override
    public boolean isUserEnabled(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && "ENABLED".equals(user.getStatus());
    }

    // ==================== SysUserService 实现 ====================

    @Override
    public Page<SysUser> page(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword));
        }
        wrapper.orderByAsc(SysUser::getId);
        return sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public SysUser getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        return user;
    }

    @Override
    public SysUser getByUsername(String username) {
        return sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
    }

    @Override
    @Transactional
    public void create(SysUser user) {
        // 校验用户名唯一性
        SysUser existing = getByUsername(user.getUsername());
        if (existing != null) {
            throw new BusinessException("用户名已存在: " + user.getUsername());
        }
        // BCrypt 加密密码
        user.setPassword(BCrypt.hashpw(user.getPassword()));
        sysUserMapper.insert(user);
        log.info("用户创建成功: {}", user.getUsername());
    }

    @Override
    @Transactional
    public void update(SysUser user) {
        SysUser dbUser = getById(user.getId());
        // 不修改密码和用户名
        dbUser.setRealName(user.getRealName());
        dbUser.setPhone(user.getPhone());
        dbUser.setEmail(user.getEmail());
        dbUser.setRoleCode(user.getRoleCode());
        sysUserMapper.updateById(dbUser);
        log.info("用户信息更新: {}", dbUser.getUsername());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String newPassword) {
        SysUser user = getById(userId);
        user.setPassword(BCrypt.hashpw(newPassword));
        sysUserMapper.updateById(user);
        log.info("用户 {} 密码已修改", user.getUsername());
    }

    @Override
    @Transactional
    public void toggleStatus(Long userId) {
        SysUser user = getById(userId);
        String newStatus = "ENABLED".equals(user.getStatus()) ? "DISABLED" : "ENABLED";
        user.setStatus(newStatus);
        sysUserMapper.updateById(user);
        log.info("用户 {} 状态切换为 {}", user.getUsername(), newStatus);
    }

    @Override
    public List<String> getAllRoleCodes() {
        return List.of("ADMIN", "OPERATOR", "WAREHOUSE", "SUPERVISOR");
    }
}
