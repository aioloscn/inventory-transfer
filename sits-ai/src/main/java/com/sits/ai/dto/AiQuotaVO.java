package com.sits.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 额度 VO。
 */
public class AiQuotaVO {

    private String provider;
    private String modelName;
    private String displayName;
    /** 当前剩余余额（来自 API） */
    private BigDecimal remainingAmount;
    private String currency;
    private LocalDateTime lastRefreshTime;
    private String message;

    // getters & setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getLastRefreshTime() { return lastRefreshTime; }
    public void setLastRefreshTime(LocalDateTime lastRefreshTime) { this.lastRefreshTime = lastRefreshTime; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
