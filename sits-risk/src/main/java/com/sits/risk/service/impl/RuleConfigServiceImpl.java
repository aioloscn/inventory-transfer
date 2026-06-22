package com.sits.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.risk.entity.RuleConfig;
import com.sits.risk.mapper.RuleConfigMapper;
import com.sits.risk.service.RuleConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则配置服务实现。
 *
 * <p>使用本地缓存 {@code valueCache} 减少 DB 查询，写操作后刷新缓存。
 */
@Service
public class RuleConfigServiceImpl implements RuleConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuleConfigServiceImpl.class);

    private final RuleConfigMapper ruleConfigMapper;

    /** 本地值缓存：configKey → configValue */
    private final Map<String, String> valueCache = new ConcurrentHashMap<>();

    public RuleConfigServiceImpl(RuleConfigMapper ruleConfigMapper) {
        this.ruleConfigMapper = ruleConfigMapper;
        // 启动时加载全部配置到缓存
        refreshCache();
    }

    @Override
    public List<RuleConfig> listAll() {
        return ruleConfigMapper.selectList(
                new LambdaQueryWrapper<RuleConfig>()
                        .orderByAsc(RuleConfig::getConfigKey));
    }

    @Override
    public String getValue(String key, String defaultValue) {
        // 优先从缓存读取
        String cached = valueCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中时查库
        RuleConfig config = ruleConfigMapper.selectOne(
                new LambdaQueryWrapper<RuleConfig>()
                        .eq(RuleConfig::getConfigKey, key));
        if (config != null) {
            valueCache.put(key, config.getConfigValue());
            return config.getConfigValue();
        }
        // 都不存在时返回默认值并打印警告
        log.warn("规则配置 {} 不存在，使用默认值: {}", key, defaultValue);
        return defaultValue;
    }

    @Override
    public int getIntValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("规则配置 {} 的值无法转为 int，使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public double getDoubleValue(String key, double defaultValue) {
        try {
            return Double.parseDouble(getValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("规则配置 {} 的值无法转为 double，使用默认值: {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    @Transactional
    public void saveOrUpdate(String key, String value, String description) {
        RuleConfig existing = ruleConfigMapper.selectOne(
                new LambdaQueryWrapper<RuleConfig>()
                        .eq(RuleConfig::getConfigKey, key));
        if (existing != null) {
            // 更新已有配置
            existing.setConfigValue(value);
            if (description != null && !description.isBlank()) {
                existing.setDescription(description);
            }
            ruleConfigMapper.updateById(existing);
            log.info("规则配置已更新: {} = {}", key, value);
        } else {
            // 新增配置
            RuleConfig config = new RuleConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            if (description != null && !description.isBlank()) {
                config.setDescription(description);
            }
            ruleConfigMapper.insert(config);
            log.info("规则配置已新增: {} = {}", key, value);
        }
        // 刷新缓存
        valueCache.put(key, value);
    }

    /**
     * 刷新本地缓存，从数据库加载全部配置。
     */
    private void refreshCache() {
        List<RuleConfig> all = ruleConfigMapper.selectList(null);
        valueCache.clear();
        for (RuleConfig config : all) {
            valueCache.put(config.getConfigKey(), config.getConfigValue());
        }
        log.info("规则配置缓存已刷新，共 {} 项", all.size());
    }
}
