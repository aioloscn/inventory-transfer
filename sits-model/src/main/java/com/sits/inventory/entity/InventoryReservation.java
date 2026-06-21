package com.sits.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 库存预占记录 — 审批通过后锁定库存的预占凭证。
 *
 * <p>唯一约束 (transfer_order_id, sku_id, warehouse_id) 保证幂等。
 * <p>生命周期：RESERVED → WRITTEN_OFF（出库核销）/ RELEASED（驳回/撤销释放）。
 */
@TableName("inventory_reservation")
public class InventoryReservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预占编号 */
    private String reservationNo;

    /** 调拨单ID */
    private Long transferOrderId;

    /** SKU ID */
    private Long skuId;

    /** 仓库ID */
    private Long warehouseId;

    /** 预占数量 */
    private Integer quantity;

    /** 预占状态: RESERVED / WRITTEN_OFF / RELEASED */
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }

    public Long getTransferOrderId() { return transferOrderId; }
    public void setTransferOrderId(Long transferOrderId) { this.transferOrderId = transferOrderId; }

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
