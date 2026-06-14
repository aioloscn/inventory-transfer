package com.sits.common.config;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token permission loader — provides roles and permissions for each loginId.
 *
 * <p>Currently uses a hardcoded mapping. In production, query from a user-role table.
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // Permissions are currently role-based; return empty, use role checks
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // TODO: Query from DB user-role table
        // For MVP, map known admin user IDs to ADMIN role
        List<String> roles = new ArrayList<>();

        String uid = String.valueOf(loginId);

        // Default: every logged-in user gets WAREHOUSE role
        roles.add("WAREHOUSE");

        // Admin users (IDs 1-10)
        try {
            long id = Long.parseLong(uid);
            if (id <= 10) {
                roles.add("ADMIN");
                roles.add("SUPERVISOR");
                roles.add("OPERATOR");
            } else if (id <= 50) {
                roles.add("SUPERVISOR");
                roles.add("OPERATOR");
            } else {
                roles.add("OPERATOR");
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric loginId — just basic role
        }

        return roles;
    }
}
