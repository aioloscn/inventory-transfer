package com.sits.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Inventory risk entity.
 */
@TableName("inventory_risk")
public class InventoryRisk {

    private Long id;
    private String riskNo;
    private Long skuId;
    private Long warehouseId;
    private String riskType;
    private String riskLevel;
    private Integer availableStock;
    private BigDecimal avgDailySales;
    private BigDecimal supportDays;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRiskNo() { return riskNo; }
    public void setRiskNo(String riskNo) { this.riskNo = riskNo; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getRiskType() { return riskType; }
    public void setRiskType(String riskType) { this.riskType = riskType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }
    public BigDecimal getAvgDailySales() { return avgDailySales; }
    public void setAvgDailySales(BigDecimal avgDailySales) { this.avgDailySales = avgDailySales; }
    public BigDecimal getSupportDays() { return supportDays; }
    public void setSupportDays(BigDecimal supportDays) { this.supportDays = supportDays; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
