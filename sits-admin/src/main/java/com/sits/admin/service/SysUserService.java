package com.sits.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.SysUser;

import java.util.List;

/**
 * 系统用户管理服务。
 */
public interface SysUserService {

    /** 分页查询用户列表 */
    Page<SysUser> page(int pageNum, int pageSize, String keyword);

    /** 根据 ID 查询用户 */
    SysUser getById(Long id);

    /** 根据用户名查询用户 */
    SysUser getByUsername(String username);

    /** 新增用户（密码自动 BCrypt 加密） */
    void create(SysUser user);

    /** 更新用户信息（不修改密码） */
    void update(SysUser user);

    /** 修改密码 */
    void changePassword(Long userId, String newPassword);

    /** 切换启用/禁用状态 */
    void toggleStatus(Long userId);

    /** 获取所有角色编码 */
    List<String> getAllRoleCodes();
}
