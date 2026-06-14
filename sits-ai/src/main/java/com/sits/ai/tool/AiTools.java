package com.sits.ai.tool;

import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.service.InventoryService;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.service.RiskService;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferSuggestion;
import com.sits.transfer.service.TransferOrderService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only tool functions exposed to the AI Copilot via Spring AI Function Calling.
 *
 * <p>These are READ-ONLY tools — the AI can query data but cannot modify anything.
 * This aligns with the product design document: "AI 不可以做写操作".
 */
@Component
public class AiTools {

    private final InventoryService inventoryService;
    private final RiskService riskService;
    private final TransferOrderService transferOrderService;

    public AiTools(InventoryService inventoryService,
                   RiskService riskService,
                   TransferOrderService transferOrderService) {
        this.inventoryService = inventoryService;
        this.riskService = riskService;
        this.transferOrderService = transferOrderService;
    }

    /**
     * Query inventory for a specific SKU at a specific warehouse.
     */
    public WarehouseInventory queryInventory(Long skuId, Long warehouseId) {
        return inventoryService.getBySkuAndWarehouse(skuId, warehouseId);
    }

    /**
     * List all inventory for a warehouse.
     */
    public List<WarehouseInventory> listWarehouseInventory(Long warehouseId) {
        return inventoryService.listByWarehouse(warehouseId);
    }

    /**
     * Query inventory risks with optional filters.
     */
    public List<InventoryRisk> queryRisks(Long skuId, Long warehouseId, String riskType) {
        return riskService.listRisks(skuId, warehouseId, riskType, null);
    }

    /**
     * Query transfer suggestions (for AI to explain and present).
     */
    public List<TransferSuggestion> querySuggestions(Long skuId, String status) {
        return riskService.listSuggestions(skuId, status);
    }

    /**
     * Get transfer order detail by transfer number.
     */
    public TransferOrder getTransferOrder(String transferNo) {
        return transferOrderService.getByTransferNo(transferNo);
    }

    /**
     * List transfer order logs.
     */
    public List<?> listTransferLogs(String transferNo) {
        return transferOrderService.listLogs(transferNo);
    }

    /**
     * Get inventory flows for a business number.
     */
    public List<?> listInventoryFlows(String bizNo) {
        return inventoryService.listFlowsByBizNo(bizNo);
    }
}
