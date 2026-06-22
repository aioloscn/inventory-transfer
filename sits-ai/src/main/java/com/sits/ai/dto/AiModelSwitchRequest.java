package com.sits.ai.dto;

/**
 * 切换当前模型请求。
 */
public class AiModelSwitchRequest {

    private Long configId;

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }
}
