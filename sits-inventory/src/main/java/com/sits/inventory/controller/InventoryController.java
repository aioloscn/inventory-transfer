package com.sits.inventory.controller;

import com.sits.common.base.Result;
import com.sits.inventory.entity.InventoryFlow;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inventory query APIs.
 */
@RestController
@RequestMapping("/api/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{skuId}/{warehouseId}")
    public Result<WarehouseInventory> getBySkuAndWarehouse(
            @PathVariable Long skuId,
            @PathVariable Long warehouseId) {
        return Result.success(inventoryService.getBySkuAndWarehouse(skuId, warehouseId));
    }

    @GetMapping
    public Result<List<WarehouseInventory>> listByWarehouse(@RequestParam Long warehouseId) {
        return Result.success(inventoryService.listByWarehouse(warehouseId));
    }

    @GetMapping("/flows")
    public Result<List<InventoryFlow>> listFlowsByBizNo(@RequestParam String bizNo) {
        return Result.success(inventoryService.listFlowsByBizNo(bizNo));
    }
}
