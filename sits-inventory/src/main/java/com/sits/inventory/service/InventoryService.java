package com.sits.inventory.service;

import com.sits.inventory.entity.InventoryFlow;
import com.sits.inventory.entity.WarehouseInventory;

import java.util.List;

/**
 * Inventory service — handles all stock operations with concurrent safety.
 */
public interface InventoryService {

    /**
     * Get inventory for a specific SKU in a specific warehouse.
     */
    WarehouseInventory getBySkuAndWarehouse(Long skuId, Long warehouseId);

    /**
     * List all inventory records for a warehouse (paginated).
     */
    List<WarehouseInventory> listByWarehouse(Long warehouseId);

    /**
     * Lock stock for transfer (available -> locked).
     * Uses optimistic locking for concurrent safety.
     */
    void lockStock(Long skuId, Long warehouseId, int quantity, String transferNo, String operator);

    /**
     * Unlock stock (locked -> available) — used on transfer rejection or cancellation.
     */
    void unlockStock(Long skuId, Long warehouseId, int quantity, String transferNo, String operator);

    /**
     * Deduct stock on outbound (locked -> in-transit).
     */
    void outboundStock(Long skuId, Long warehouseId, int quantity, String transferNo, String operator);

    /**
     * Inbound stock (available += quantity) at target warehouse.
     */
    void inboundStock(Long skuId, Long warehouseId, int quantity, String transferNo, String operator);

    /**
     * Release in-transit stock after inbound confirmed at target.
     */
    void releaseInTransitStock(Long skuId, Long warehouseId, int quantity, String transferNo, String operator);

    /**
     * Query inventory flows by business number.
     */
    List<InventoryFlow> listFlowsByBizNo(String bizNo);
}
