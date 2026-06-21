package com.sits.risk.dto;

import java.math.BigDecimal;

/**
 * AI 解释增强上下文，包含生成 reason 所需的核心信息。
 * <p>AI 只能使用这些数据生成说明文案，不能修改调拨数量或仓库等核心字段。
 */
public class SuggestionExplainContext {

    /** 基础规则生成的reason */
    private String baseReason;

    private String riskNo;
    private Long skuId;
    private String riskLevel;
    private String targetWarehouseName;
    private Integer targetAvailableStock;
    private Integer targetSafetyStock;
    private Integer targetInTransitStock;
    private BigDecimal avgDailySales;
    private BigDecimal supportDays;
    private String sourceWarehouseName;
    private Integer sourceAvailableStock;
    private Integer sourceSafetyStock;
    private Integer sourceTransferableStock;
    private Integer suggestQuantity;
    private BigDecimal score;

    public String getBaseReason() { return baseReason; }
    public void setBaseReason(String baseReason) { this.baseReason = baseReason; }

    public String getRiskNo() { return riskNo; }
    public void setRiskNo(String riskNo) { this.riskNo = riskNo; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getTargetWarehouseName() { return targetWarehouseName; }
    public void setTargetWarehouseName(String targetWarehouseName) { this.targetWarehouseName = targetWarehouseName; }

    public Integer getTargetAvailableStock() { return targetAvailableStock; }
    public void setTargetAvailableStock(Integer targetAvailableStock) { this.targetAvailableStock = targetAvailableStock; }

    public Integer getTargetSafetyStock() { return targetSafetyStock; }
    public void setTargetSafetyStock(Integer targetSafetyStock) { this.targetSafetyStock = targetSafetyStock; }

    public Integer getTargetInTransitStock() { return targetInTransitStock; }
    public void setTargetInTransitStock(Integer targetInTransitStock) { this.targetInTransitStock = targetInTransitStock; }

    public BigDecimal getAvgDailySales() { return avgDailySales; }
    public void setAvgDailySales(BigDecimal avgDailySales) { this.avgDailySales = avgDailySales; }

    public BigDecimal getSupportDays() { return supportDays; }
    public void setSupportDays(BigDecimal supportDays) { this.supportDays = supportDays; }

    public String getSourceWarehouseName() { return sourceWarehouseName; }
    public void setSourceWarehouseName(String sourceWarehouseName) { this.sourceWarehouseName = sourceWarehouseName; }

    public Integer getSourceAvailableStock() { return sourceAvailableStock; }
    public void setSourceAvailableStock(Integer sourceAvailableStock) { this.sourceAvailableStock = sourceAvailableStock; }

    public Integer getSourceSafetyStock() { return sourceSafetyStock; }
    public void setSourceSafetyStock(Integer sourceSafetyStock) { this.sourceSafetyStock = sourceSafetyStock; }

    public Integer getSourceTransferableStock() { return sourceTransferableStock; }
    public void setSourceTransferableStock(Integer sourceTransferableStock) { this.sourceTransferableStock = sourceTransferableStock; }

    public Integer getSuggestQuantity() { return suggestQuantity; }
    public void setSuggestQuantity(Integer suggestQuantity) { this.suggestQuantity = suggestQuantity; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
}
