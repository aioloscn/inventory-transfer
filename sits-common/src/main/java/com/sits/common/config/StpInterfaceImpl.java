package com.sits.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sits.common.service.UserAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限加载器 — 根据 loginId（数字用户 ID）从 DB 查询角色。
 *
 * <p>当 loginId 不是数字格式（兼容旧版字符串 username 登录），
 * 回退为 admin 全角色。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private static final Logger log = LoggerFactory.getLogger(StpInterfaceImpl.class);

    private final UserAuthService userAuthService;

    public StpInterfaceImpl(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        String uid = String.valueOf(loginId);

        try {
            Long userId = Long.parseLong(uid);
            // 从 DB 查询用户角色
            List<String> dbRoles = userAuthService.getRolesByUserId(userId);
            if (!dbRoles.isEmpty()) {
                return dbRoles;
            }
        } catch (NumberFormatException e) {
            // 兼容旧版字符串 loginId（如直接登录 "admin"），授予全角色
            log.warn("非数字 loginId: {}, 授予全角色（兼容模式）", uid);
            roles.add("ADMIN");
            roles.add("SUPERVISOR");
            roles.add("OPERATOR");
            roles.add("WAREHOUSE");
            return roles;
        }

        // 兜底：至少给一个基础角色
        roles.add("WAREHOUSE");
        return roles;
    }
}
