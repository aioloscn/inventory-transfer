package com.sits.common.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication API — login, logout, token info.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Simple login — accepts userId and password.
     * In MVP, password is not validated (placeholder).
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestParam Long userId,
                                              @RequestParam String password) {
        // TODO: Validate credentials against DB
        StpUtil.login(userId);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.success(Map.of(
                "tokenName", tokenInfo.getTokenName(),
                "tokenValue", tokenInfo.getTokenValue(),
                "userId", String.valueOf(userId)
        ));
    }

    /**
     * Logout current user.
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * Check if current user is logged in.
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> check() {
        boolean loggedIn = StpUtil.isLogin();
        return Result.success(Map.of(
                "isLogin", loggedIn,
                "loginId", loggedIn ? StpUtil.getLoginId() : null,
                "roles", loggedIn ? StpUtil.getRoleList() : null
        ));
    }
}
