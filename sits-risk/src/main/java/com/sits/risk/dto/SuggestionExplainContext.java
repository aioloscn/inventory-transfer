package com.sits.risk.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * AI 解释增强上下文，包含生成 reason 所需的核心信息。
 * <p>AI 只能使用这些数据生成说明文案，不能修改调拨数量或仓库等核心字段。
 */
@Data
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
}
