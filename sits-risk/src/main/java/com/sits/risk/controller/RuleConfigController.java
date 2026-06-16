package com.sits.risk.controller;

import com.sits.common.base.Result;
import com.sits.risk.entity.RuleConfig;
import com.sits.risk.service.RuleConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 规则配置 API — ADMIN 角色可读写配置项。
 *
 * <p>前端页面路径: /rule
 */
@RestController
@RequestMapping("/api/rules")
public class RuleConfigController {

    private final RuleConfigService ruleConfigService;

    public RuleConfigController(RuleConfigService ruleConfigService) {
        this.ruleConfigService = ruleConfigService;
    }

    /**
     * 获取全部规则配置列表。
     */
    @GetMapping
    public Result<List<RuleConfig>> listAll() {
        return Result.success(ruleConfigService.listAll());
    }

    /**
     * 更新单条配置。
     *
     * <p>请求体: {"configKey": "...", "configValue": "...", "description": "..."}
     */
    @PutMapping("/{configKey}")
    public Result<Void> update(@PathVariable String configKey,
                               @RequestBody Map<String, String> body) {
        String value = body.get("configValue");
        String description = body.get("description");
        ruleConfigService.saveOrUpdate(configKey, value, description);
        return Result.success();
    }
}
