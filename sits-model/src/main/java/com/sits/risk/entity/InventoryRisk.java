package com.sits.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inventory risk entity.
 */
@TableName("inventory_risk")
@Data
public class InventoryRisk {

    private Long id;
    private String riskNo;
    private Long skuId;
    private Long warehouseId;
    private String scanBatchNo;
    private LocalDateTime latestScanTime;
    private String riskType;
    private String riskLevel;
    private String riskFingerprint;
    private Integer availableStock;
    private BigDecimal avgDailySales;
    private BigDecimal supportDays;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 构造风险指纹：skuId_warehouseId_riskType
     */
    public static String fingerprint(Long skuId, Long warehouseId, String riskType) {
        return skuId + "_" + warehouseId + "_" + riskType;
    }
}
