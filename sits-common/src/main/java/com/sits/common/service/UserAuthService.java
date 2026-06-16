package com.sits.common.service;

import java.util.List;

/**
 * 用户认证服务接口 — 定义在 sits-common，由 sits-admin 实现。
 *
 * <p>用于 AuthController 登录校验和 StpInterfaceImpl 角色查询，
 * 避免 sits-common 直接依赖 sits-admin 造成循环引用。
 */
public interface UserAuthService {

    /**
     * 验证用户名密码，登录成功返回用户 ID，失败返回 null。
     */
    Long validate(String username, String rawPassword);

    /**
     * 根据用户 ID 获取角色编码列表。
     */
    List<String> getRolesByUserId(Long userId);

    /**
     * 检查用户是否存在且未被禁用。
     */
    boolean isUserEnabled(Long userId);
}
