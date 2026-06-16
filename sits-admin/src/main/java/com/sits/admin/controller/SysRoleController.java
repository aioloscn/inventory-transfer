package com.sits.admin.controller;

import com.sits.admin.entity.SysRole;
import com.sits.admin.service.SysRoleService;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统角色管理 API — ADMIN 角色可操作。
 *
 * <p>角色编码是系统固定的（ADMIN/OPERATOR/WAREHOUSE/SUPERVISOR），
 * 本接口用于查询角色信息、编辑描述、查看各角色的用户数量。
 */
@RestController
@RequestMapping("/api/roles")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    /**
     * 获取全部角色列表，附带各角色的用户数量统计。
     */
    @GetMapping
    public Result<Map<String, Object>> listAll() {
        List<SysRole> roles = sysRoleService.listAll();
        Map<String, Long> userCountMap = sysRoleService.countUsersByRole();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roles);
        result.put("userCountMap", userCountMap);
        return Result.success(result);
    }

    /**
     * 获取单个角色详情。
     */
    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable Long id) {
        return Result.success(sysRoleService.getById(id));
    }

    /**
     * 更新角色描述。
     *
     * <p>请求体: {"description": "新的角色说明"}
     */
    @PutMapping("/{id}/description")
    public Result<Void> updateDescription(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        String description = body.get("description");
        sysRoleService.updateDescription(id, description);
        return Result.success();
    }
}
