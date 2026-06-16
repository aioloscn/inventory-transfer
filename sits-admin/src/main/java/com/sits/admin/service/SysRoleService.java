package com.sits.admin.service;

import com.sits.admin.entity.SysRole;

import java.util.List;
import java.util.Map;

/**
 * 系统角色管理服务。
 */
public interface SysRoleService {

    /** 获取全部已启用的角色列表 */
    List<SysRole> listAll();

    /** 根据 ID 查询角色 */
    SysRole getById(Long id);

    /** 更新角色描述 */
    void updateDescription(Long id, String description);

    /** 获取各角色的用户数量统计 */
    Map<String, Long> countUsersByRole();
}
