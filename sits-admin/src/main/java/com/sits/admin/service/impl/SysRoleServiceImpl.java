package com.sits.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.admin.entity.SysRole;
import com.sits.admin.entity.SysUser;
import com.sits.admin.mapper.SysRoleMapper;
import com.sits.admin.mapper.SysUserMapper;
import com.sits.admin.service.SysRoleService;
import com.sits.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统角色管理服务实现。
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    private static final Logger log = LoggerFactory.getLogger(SysRoleServiceImpl.class);

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;

    public SysRoleServiceImpl(SysRoleMapper sysRoleMapper, SysUserMapper sysUserMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<SysRole> listAll() {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .orderByAsc(SysRole::getId));
    }

    @Override
    public SysRole getById(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在: " + id);
        }
        return role;
    }

    @Override
    @Transactional
    public void updateDescription(Long id, String description) {
        SysRole role = getById(id);
        role.setDescription(description);
        sysRoleMapper.updateById(role);
        log.info("角色 {} 描述已更新", role.getRoleCode());
    }

    @Override
    public Map<String, Long> countUsersByRole() {
        // 查出所有启用状态的用户，按 roleCode 分组统计数量
        List<SysUser> allUsers = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "ENABLED"));
        Map<String, Long> result = new LinkedHashMap<>();
        for (SysUser user : allUsers) {
            String code = user.getRoleCode();
            result.merge(code, 1L, Long::sum);
        }
        // 确保四种角色都有条目（即使数量为 0）
        for (String code : List.of("ADMIN", "OPERATOR", "WAREHOUSE", "SUPERVISOR")) {
            result.putIfAbsent(code, 0L);
        }
        return result;
    }
}
