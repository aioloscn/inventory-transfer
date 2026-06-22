package com.sits.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 模型配置 VO（前端展示，不含明文 API Key）。
 */
public class AiModelConfigVO {

    private Long id;
    private String provider;
    private String modelName;
    private String displayName;
    private String maskedApiKey;
    private String baseUrl;
    private Integer enabled;
    private Integer defaultFlag;
    private Integer currentFlag;
    private Integer supportStream;
    private Integer supportToolCall;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMaskedApiKey() { return maskedApiKey; }
    public void setMaskedApiKey(String maskedApiKey) { this.maskedApiKey = maskedApiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getDefaultFlag() { return defaultFlag; }
    public void setDefaultFlag(Integer defaultFlag) { this.defaultFlag = defaultFlag; }

    public Integer getCurrentFlag() { return currentFlag; }
    public void setCurrentFlag(Integer currentFlag) { this.currentFlag = currentFlag; }

    public Integer getSupportStream() { return supportStream; }
    public void setSupportStream(Integer supportStream) { this.supportStream = supportStream; }

    public Integer getSupportToolCall() { return supportToolCall; }
    public void setSupportToolCall(Integer supportToolCall) { this.supportToolCall = supportToolCall; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
