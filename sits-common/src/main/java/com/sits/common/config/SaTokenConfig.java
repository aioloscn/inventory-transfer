package com.sits.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token route-level authorization configuration.
 *
 * <p>Four roles as defined in the product design:
 * <ul>
 *   <li>ADMIN — system administrator, full access</li>
 *   <li>OPERATOR — inventory operator, can view/operate inventory and transfers</li>
 *   <li>WAREHOUSE — warehouse manager, can execute outbound/inbound</li>
 *   <li>SUPERVISOR — supply chain supervisor, can approve/reject transfer orders</li>
 * </ul>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {

            // --- Public endpoints (no auth) ---
            SaRouter.match("/api/auth/**").stop();

            // --- AI Copilot — requires login (any role) ---
            SaRouter.match("/api/ai/**")
                    .check(r -> StpUtil.checkLogin());

            // --- ADMIN only: warehouse/SKU CRUD, risk management, user & role management ---
            SaRouter.match("/api/warehouses/**")
                    .match("/api/skus/**")
                    .match("/api/users/**")
                    .match("/api/roles/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN"));

            // --- Inventory read — any logged-in user ---
            SaRouter.match("/api/inventories/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN", "OPERATOR", "WAREHOUSE", "SUPERVISOR"));

            // --- Transfer operations — ADMIN, OPERATOR, WAREHOUSE, SUPERVISOR ---
            SaRouter.match("/api/transfer-orders/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN", "OPERATOR", "WAREHOUSE", "SUPERVISOR"));

            // --- Approvals — ADMIN, SUPERVISOR ---
            SaRouter.match("/api/approvals/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN", "SUPERVISOR"));

            // --- Risk & suggestions — ADMIN, OPERATOR ---
            SaRouter.match("/api/risks/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN", "OPERATOR"));

            // --- Rule config — ADMIN only ---
            SaRouter.match("/api/rules/**")
                    .check(r -> StpUtil.checkRoleOr("ADMIN"));

            // --- All other APIs require login ---
            SaRouter.match("/api/**")
                    .check(r -> StpUtil.checkLogin());

        })).addPathPatterns("/api/**");
    }
}
