package com.sits.risk.service;

import com.sits.risk.entity.RuleConfig;

import java.util.List;

/**
 * 规则配置服务 — 管理系统级可调参数。
 */
public interface RuleConfigService {

    /**
     * 获取全部配置项列表。
     */
    List<RuleConfig> listAll();

    /**
     * 根据配置键获取字符串值。
     *
     * @param key          配置键
     * @param defaultValue 默认值（配置不存在时返回）
     */
    String getValue(String key, String defaultValue);

    /**
     * 根据配置键获取 int 值。
     */
    int getIntValue(String key, int defaultValue);

    /**
     * 根据配置键获取 double 值。
     */
    double getDoubleValue(String key, double defaultValue);

    /**
     * 更新或新增一条配置。
     *
     * @param key   配置键
     * @param value 配置值
     * @param description 配置说明
     */
    void saveOrUpdate(String key, String value, String description);
}
