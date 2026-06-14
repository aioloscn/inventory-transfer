package com.sits.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.sits.common.base.BaseEntity;

/**
 * Warehouse inventory — each SKU in each warehouse has independent inventory.
 * Uses optimistic locking ({@code version}) for concurrent safety.
 */
@TableName("warehouse_inventory")
public class WarehouseInventory extends BaseEntity {

    private Long skuId;
    private Long warehouseId;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer inTransitStock;
    private Integer safetyStock;
    private Integer maxStock;

    @Version
    private Integer version;

    // -- getters / setters --

    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }

    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }

    public Integer getAvailableStock() { return availableStock; }
    public void setAvailableStock(Integer availableStock) { this.availableStock = availableStock; }

    public Integer getLockedStock() { return lockedStock; }
    public void setLockedStock(Integer lockedStock) { this.lockedStock = lockedStock; }

    public Integer getInTransitStock() { return inTransitStock; }
    public void setInTransitStock(Integer inTransitStock) { this.inTransitStock = inTransitStock; }

    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer safetyStock) { this.safetyStock = safetyStock; }

    public Integer getMaxStock() { return maxStock; }
    public void setMaxStock(Integer maxStock) { this.maxStock = maxStock; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    /**
     * Total inventory = available + locked + in-transit.
     */
    public int getTotalStock() {
        return nvl(availableStock) + nvl(lockedStock) + nvl(inTransitStock);
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
