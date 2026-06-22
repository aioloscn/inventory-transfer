package com.sits.ai.service;

import com.sits.ai.dto.AiModelConfigVO;
import com.sits.ai.dto.AiQuotaVO;
import com.sits.ai.entity.AiModelConfig;

import java.util.List;

/**
 * AI 模型配置服务接口。
 */
public interface AiModelConfigService {

    /** 查询所有模型配置列表（脱敏） */
    List<AiModelConfigVO> listAll();

    /** 新增模型配置 */
    AiModelConfigVO create(com.sits.ai.dto.AiModelConfigCreateRequest request);

    /** 修改模型配置 */
    AiModelConfigVO update(com.sits.ai.dto.AiModelConfigUpdateRequest request);

    /** 删除模型配置 */
    void delete(Long id);

    /** 切换当前模型 */
    AiModelConfigVO switchModel(Long configId);

    /** 查询当前使用的模型 */
    AiModelConfigVO getCurrentModel();

    /** 获取当前使用的模型原始配置（含解密后的 apiKey，仅内部调用） */
    AiModelConfig getCurrentModelConfig();

    /** 查询 DeepSeek 额度信息 */
    AiQuotaVO getDeepSeekQuota();
}
