package com.sits.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.SysUser;
import com.sits.admin.service.SysUserService;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统用户管理 API — ADMIN 角色可操作。
 *
 * <p>提供用户的 CRUD、角色查询、状态切换、密码修改功能。
 */
@RestController
@RequestMapping("/api/users")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 分页查询用户列表，支持按用户名/姓名模糊搜索。
     */
    @GetMapping
    public Result<Page<SysUser>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(sysUserService.page(pageNum, pageSize, keyword));
    }

    /**
     * 根据 ID 查询用户详情。
     */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getById(id));
    }

    /**
     * 新增用户。
     *
     * <p>请求体: {"username":"...", "password":"...", "realName":"...", "roleCode":"...", ...}
     */
    @PostMapping
    public Result<Void> create(@RequestBody SysUser user) {
        sysUserService.create(user);
        return Result.success();
    }

    /**
     * 更新用户信息（不修改密码）。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        sysUserService.update(user);
        return Result.success();
    }

    /**
     * 修改密码。
     *
     * <p>请求体: {"newPassword":"..."}
     */
    @PutMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        sysUserService.changePassword(id, body.get("newPassword"));
        return Result.success();
    }

    /**
     * 切换用户启用/禁用状态。
     */
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        sysUserService.toggleStatus(id);
        return Result.success();
    }

    /**
     * 获取全部角色编码列表（前端下拉选项用）。
     */
    @GetMapping("/roles")
    public Result<List<String>> getRoleCodes() {
        return Result.success(sysUserService.getAllRoleCodes());
    }
}
