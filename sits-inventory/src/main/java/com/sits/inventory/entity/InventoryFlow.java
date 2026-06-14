package com.sits.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Inventory flow — records every stock change.
 * Uses unique constraint (biz_type, biz_no, change_type) for idempotency.
 */
@TableName("inventory_flow")
public class InventoryFlow {

    private Long id;
    private String flowNo;
    private Long skuId;
    private Long warehouseId;
    private String bizType;
    private String bizNo;
    private String changeType;
    private Integer beforeAvailable;
    private Integer changeQuantity;
    private Integer afterAvailable;
    private String operator;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFlowNo() { return flowNo; }
    public void setFlowNo(String flowNo) { this.flowNo = flowNo; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizNo() { return bizNo; }
    public void setBizNo(String bizNo) { this.bizNo = bizNo; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public Integer getBeforeAvailable() { return beforeAvailable; }
    public void setBeforeAvailable(Integer beforeAvailable) { this.beforeAvailable = beforeAvailable; }

    public Integer getChangeQuantity() { return changeQuantity; }
    public void setChangeQuantity(Integer changeQuantity) { this.changeQuantity = changeQuantity; }

    public Integer getAfterAvailable() { return afterAvailable; }
    public void setAfterAvailable(Integer afterAvailable) { this.afterAvailable = afterAvailable; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
