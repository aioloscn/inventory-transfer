package com.sits.common.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.sits.common.base.Result;
import com.sits.common.dto.LoginRequest;
import com.sits.common.service.UserAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证 API — 登录时调用 UserAuthService 验证 DB 中的用户名密码。
 *
 * <p>登录成功后将用户 ID 作为 Sa-Token loginId，后续 StpInterfaceImpl
 * 会根据 loginId 从 DB 查询角色。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthService userAuthService;

    public AuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    /**
     * 登录 — 验证 DB 中的用户名密码，成功后返回 token。
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        // 通过 UserAuthService 校验 DB 中的用户名密码
        Long userId = userAuthService.validate(request.getUserId(), request.getPassword());
        if (userId == null) {
            return Result.fail(401, "用户名或密码错误");
        }

        // 以数字用户 ID 作为 Sa-Token loginId
        StpUtil.login(userId);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return Result.success(Map.of(
                "tokenName", tokenInfo.getTokenName(),
                "tokenValue", tokenInfo.getTokenValue(),
                "userId", String.valueOf(userId)
        ));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 检查登录状态，返回当前用户信息和角色。
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
