package com.sits.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 规则配置实体 — 系统级可调参数，以键值对形式存储。
 *
 * <p>典型配置项：
 * <ul>
 *   <li>shortage_high_days — 高风险缺货阈值（天）</li>
 *   <li>shortage_medium_days — 中风险缺货阈值（天）</li>
 *   <li>overstock_days — 积压阈值（天）</li>
 *   <li>overstock_ratio — 积压库存比例</li>
 *   <li>avg_sales_lookback_days — 日均销量回溯天数</li>
 *   <li>suggestion_expire_days — 建议过期天数</li>
 *   <li>cost_weight / time_weight / stock_health_weight — 评分权重</li>
 * </ul>
 */
@TableName("rule_config")
public class RuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键（唯一） */
    private String configKey;

    /** 配置值（字符串存储，使用时按需转换） */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 最近更新时间 */
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
