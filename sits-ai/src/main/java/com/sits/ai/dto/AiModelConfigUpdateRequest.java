package com.sits.ai.dto;

/**
 * 修改 AI 模型配置请求。
 */
public class AiModelConfigUpdateRequest {

    private Long id;
    private String displayName;
    private String apiKey;
    private String baseUrl;
    private Integer enabled;
    private Integer supportStream;
    private Integer supportToolCall;
    private String remark;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public Integer getSupportStream() { return supportStream; }
    public void setSupportStream(Integer supportStream) { this.supportStream = supportStream; }

    public Integer getSupportToolCall() { return supportToolCall; }
    public void setSupportToolCall(Integer supportToolCall) { this.supportToolCall = supportToolCall; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
