package com.sits.ai.controller;

import com.sits.ai.dto.*;
import com.sits.ai.service.AiModelConfigService;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置管理接口。
 *
 * <p>权限控制说明（部署时在 SaTokenConfig 中配置）：
 * <ul>
 *   <li>列表查询：所有已登录用户可访问</li>
 *   <li>新增/修改/删除/切换/额度查询：仅 ADMIN 角色可访问</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/models")
public class AiModelConfigController {

    private final AiModelConfigService modelConfigService;

    public AiModelConfigController(AiModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    /** 查询所有模型配置列表 */
    @GetMapping
    public Result<List<AiModelConfigVO>> list() {
        return Result.success(modelConfigService.listAll());
    }

    /** 新增模型配置（仅 ADMIN） */
    @PostMapping
    public Result<AiModelConfigVO> create(@RequestBody AiModelConfigCreateRequest request) {
        return Result.success(modelConfigService.create(request));
    }

    /** 修改模型配置（仅 ADMIN） */
    @PutMapping("/{id}")
    public Result<AiModelConfigVO> update(@PathVariable Long id, @RequestBody AiModelConfigUpdateRequest request) {
        request.setId(id);
        return Result.success(modelConfigService.update(request));
    }

    /** 删除模型配置（仅 ADMIN） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return Result.success();
    }

    /** 切换当前使用的模型（仅 ADMIN） */
    @PostMapping("/switch")
    public Result<AiModelConfigVO> switchModel(@RequestBody AiModelSwitchRequest request) {
        return Result.success("已切换模型", modelConfigService.switchModel(request.getConfigId()));
    }

    /** 查询当前使用的模型 */
    @GetMapping("/current")
    public Result<AiModelConfigVO> current() {
        return Result.success(modelConfigService.getCurrentModel());
    }
}
