package com.sits.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sits.ai.dto.*;
import com.sits.ai.entity.AiModelConfig;
import com.sits.ai.mapper.AiModelConfigMapper;
import com.sits.ai.provider.AiModelClientFactory;
import com.sits.ai.provider.DeepSeekModelProviderClient;
import com.sits.ai.service.AiModelConfigService;
import com.sits.common.exception.BusinessException;
import com.sits.common.util.ApiKeyCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 模型配置服务实现。
 */
@Service
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigServiceImpl.class);

    private final AiModelConfigMapper modelConfigMapper;
    private final ApiKeyCryptoUtil cryptoUtil;
    private final AiModelClientFactory clientFactory;

    public AiModelConfigServiceImpl(AiModelConfigMapper modelConfigMapper,
                                     ApiKeyCryptoUtil cryptoUtil,
                                     AiModelClientFactory clientFactory) {
        this.modelConfigMapper = modelConfigMapper;
        this.cryptoUtil = cryptoUtil;
        this.clientFactory = clientFactory;
    }

    @Override
    public List<AiModelConfigVO> listAll() {
        List<AiModelConfig> configs = modelConfigMapper.selectList(
                new LambdaQueryWrapper<AiModelConfig>().orderByAsc(AiModelConfig::getId));
        return configs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AiModelConfigVO create(AiModelConfigCreateRequest request) {
        if (request.getProvider() == null || request.getProvider().isBlank()) {
            throw new BusinessException("供应商不能为空");
        }
        if (request.getModelName() == null || request.getModelName().isBlank()) {
            throw new BusinessException("模型名称不能为空");
        }
        // 唯一性校验
        Long count = modelConfigMapper.selectCount(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getProvider, request.getProvider())
                        .eq(AiModelConfig::getModelName, request.getModelName()));
        if (count > 0) {
            throw new BusinessException("该供应商的模型名称已存在");
        }

        AiModelConfig config = new AiModelConfig();
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setDisplayName(request.getDisplayName());
        config.setApiKey(encryptApiKey(request.getApiKey()));
        config.setBaseUrl(request.getBaseUrl());
        config.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        config.setDefaultFlag(0);
        config.setCurrentFlag(0);
        config.setSupportStream(request.getSupportStream() != null ? request.getSupportStream() : 1);
        config.setSupportToolCall(request.getSupportToolCall() != null ? request.getSupportToolCall() : 1);
        config.setRemark(request.getRemark());
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());

        modelConfigMapper.insert(config);
        log.info("Created AI model config: provider={}, model={}", config.getProvider(), config.getModelName());
        return toVO(config);
    }

    @Override
    @Transactional
    public AiModelConfigVO update(AiModelConfigUpdateRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("配置 ID 不能为空");
        }
        AiModelConfig config = modelConfigMapper.selectById(request.getId());
        if (config == null) {
            throw new BusinessException("模型配置不存在");
        }

        if (request.getDisplayName() != null) {
            config.setDisplayName(request.getDisplayName());
        }
        // apiKey 有传则更新，不传则不修改
        if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
            config.setApiKey(encryptApiKey(request.getApiKey()));
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(request.getBaseUrl());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getSupportStream() != null) {
            config.setSupportStream(request.getSupportStream());
        }
        if (request.getSupportToolCall() != null) {
            config.setSupportToolCall(request.getSupportToolCall());
        }
        if (request.getRemark() != null) {
            config.setRemark(request.getRemark());
        }
        config.setUpdateTime(LocalDateTime.now());

        modelConfigMapper.updateById(config);
        log.info("Updated AI model config: id={}", config.getId());
        return toVO(config);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("模型配置不存在");
        }
        if (config.getCurrentFlag() != null && config.getCurrentFlag() == 1) {
            throw new BusinessException("当前正在使用的模型不允许删除，请先切换到其他模型");
        }
        if (config.getDefaultFlag() != null && config.getDefaultFlag() == 1) {
            throw new BusinessException("默认模型不允许删除");
        }
        modelConfigMapper.deleteById(id);
        log.info("Deleted AI model config: id={}, provider={}, model={}", id, config.getProvider(), config.getModelName());
    }

    @Override
    @Transactional
    public AiModelConfigVO switchModel(Long configId) {
        AiModelConfig target = modelConfigMapper.selectById(configId);
        if (target == null) {
            throw new BusinessException("模型配置不存在");
        }
        if (target.getEnabled() == null || target.getEnabled() != 1) {
            throw new BusinessException("该模型未启用，无法切换");
        }
        if (target.getSupportStream() == null || target.getSupportStream() != 1) {
            throw new BusinessException("该模型不支持 SSE 流式输出，无法用于 AI Copilot");
        }

        // 将所有 current_flag 置为 0
        LambdaUpdateWrapper<AiModelConfig> resetWrapper = new LambdaUpdateWrapper<>();
        resetWrapper.set(AiModelConfig::getCurrentFlag, 0)
                .set(AiModelConfig::getUpdateTime, LocalDateTime.now());
        modelConfigMapper.update(null, resetWrapper);

        // 将目标 current_flag 置为 1
        target.setCurrentFlag(1);
        target.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(target);

        log.info("Switched AI model to: provider={}, model={}", target.getProvider(), target.getModelName());
        return toVO(target);
    }

    @Override
    public AiModelConfigVO getCurrentModel() {
        AiModelConfig config = modelConfigMapper.selectOne(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getCurrentFlag, 1));
        if (config == null) {
            throw new BusinessException("未配置当前使用的 AI 模型，请在模型管理中设置");
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("当前 AI 模型未启用，请在模型管理中启用");
        }
        return toVO(config);
    }

    @Override
    public AiModelConfig getCurrentModelConfig() {
        AiModelConfig config = modelConfigMapper.selectOne(
                new LambdaQueryWrapper<AiModelConfig>().eq(AiModelConfig::getCurrentFlag, 1));
        if (config == null) {
            throw new BusinessException("未配置当前使用的 AI 模型");
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException("当前 AI 模型未启用");
        }
        if (config.getSupportStream() == null || config.getSupportStream() != 1) {
            throw new BusinessException("当前 AI 模型不支持 SSE 流式输出");
        }
        // 拷贝后解密，避免污染 MyBatis 一级缓存的实体
        AiModelConfig result = new AiModelConfig();
        BeanUtils.copyProperties(config, result);
        try {
            String decryptedKey = cryptoUtil.decrypt(result.getApiKey());
            result.setApiKey(decryptedKey);
        } catch (Exception e) {
            log.error("Failed to decrypt API Key for current model", e);
            throw new BusinessException("API Key 解密失败");
        }
        if (result.getApiKey() == null || result.getApiKey().isEmpty()) {
            throw new BusinessException("当前模型 API Key 为空，请在模型管理中配置");
        }
        return result;
    }

    @Override
    public AiQuotaVO getDeepSeekQuota() {
        AiQuotaVO vo = new AiQuotaVO();
        vo.setProvider("DEEPSEEK");
        vo.setLastRefreshTime(LocalDateTime.now());

        AiModelConfig deepSeekConfig = modelConfigMapper.selectOne(
                new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getProvider, "DEEPSEEK")
                        .eq(AiModelConfig::getEnabled, 1)
                        .last("LIMIT 1"));
        if (deepSeekConfig == null) {
            vo.setMessage("未找到已启用的 DeepSeek 配置");
            return vo;
        }

        vo.setModelName(deepSeekConfig.getModelName());
        vo.setDisplayName(deepSeekConfig.getDisplayName());

        try {
            String apiKey = cryptoUtil.decrypt(deepSeekConfig.getApiKey());
            if (apiKey.isEmpty()) {
                vo.setMessage("DeepSeek API Key 未配置");
                return vo;
            }

            DeepSeekModelProviderClient deepSeekClient = clientFactory.getDeepSeekClient();
            String balanceJson = deepSeekClient.queryBalance(apiKey);
            parseBalanceResponse(balanceJson, vo);
        } catch (Exception e) {
            log.error("Failed to query DeepSeek balance", e);
            vo.setMessage("余额查询失败: " + e.getMessage());
        }

        return vo;
    }

    // --- 内部方法 ---

    private AiModelConfigVO toVO(AiModelConfig config) {
        AiModelConfigVO vo = new AiModelConfigVO();
        vo.setId(config.getId());
        vo.setProvider(config.getProvider());
        vo.setModelName(config.getModelName());
        vo.setDisplayName(config.getDisplayName());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setEnabled(config.getEnabled());
        vo.setDefaultFlag(config.getDefaultFlag());
        vo.setCurrentFlag(config.getCurrentFlag());
        vo.setSupportStream(config.getSupportStream());
        vo.setSupportToolCall(config.getSupportToolCall());
        vo.setRemark(config.getRemark());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());

        // 脱敏处理：返回 maskedApiKey
        try {
            String decryptedKey = cryptoUtil.decrypt(config.getApiKey());
            vo.setMaskedApiKey(ApiKeyCryptoUtil.mask(decryptedKey));
        } catch (Exception e) {
            vo.setMaskedApiKey("****");
        }

        return vo;
    }

    private String encryptApiKey(String plainKey) {
        if (plainKey == null || plainKey.isEmpty()) {
            return "";
        }
        return cryptoUtil.encrypt(plainKey);
    }

    /**
     * 解析 DeepSeek balance 接口响应，只提取当前剩余余额。
     */
    private void parseBalanceResponse(String json, AiQuotaVO vo) {
        if (json == null || json.isEmpty()) return;

        try {
            int balanceInfosIdx = json.indexOf("\"balance_infos\"");
            if (balanceInfosIdx < 0) return;

            int arrayStart = json.indexOf('[', balanceInfosIdx);
            int arrayEnd = json.indexOf(']', arrayStart);
            if (arrayStart < 0 || arrayEnd < 0) return;

            String itemsJson = json.substring(arrayStart + 1, arrayEnd);
            int objStart = itemsJson.indexOf('{');
            int objEnd = itemsJson.indexOf('}', objStart);
            if (objStart < 0 || objEnd < 0) return;

            String obj = itemsJson.substring(objStart, objEnd + 1);

            String currency = extractStringField(obj, "currency");
            if (currency != null) vo.setCurrency(currency);

            String remaining = extractStringField(obj, "total_balance");
            if (remaining != null) {
                vo.setRemainingAmount(new BigDecimal(remaining));
            }
        } catch (Exception e) {
            log.warn("Failed to parse DeepSeek balance response", e);
        }
    }

    private String extractStringField(String json, String fieldName) {
        int keyIdx = json.indexOf("\"" + fieldName + "\"");
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx);
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
