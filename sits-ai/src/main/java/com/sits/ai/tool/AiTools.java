package com.sits.ai.tool;

import com.sits.common.base.PageQuery;
import com.sits.common.enums.RiskLevel;
import com.sits.common.enums.RiskStatus;
import com.sits.common.enums.RiskType;
import com.sits.inventory.entity.InventoryFlow;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.service.InventoryService;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.service.RiskService;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferSuggestion;
import com.sits.transfer.service.TransferOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only tool functions exposed to the AI Copilot via Spring AI Function Calling.
 *
 * <p>These are READ-ONLY tools — the AI can query data but cannot modify anything.
 * This aligns with the product design document: "AI 不可以做写操作".
 */
@Component
public class AiTools {

    private static final Logger log = LoggerFactory.getLogger(AiTools.class);
    private static final int AI_QUERY_LIMIT = 100;

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
    public List<WarehouseInventory> listWarehouseInventory(Long warehouseId, Integer limit) {
        List<WarehouseInventory> list = inventoryService.listByWarehouse(warehouseId);
        if (limit != null && limit > 0 && list.size() > limit) {
            return list.subList(0, Math.min(limit, AI_QUERY_LIMIT));
        }
        if (list.size() > AI_QUERY_LIMIT) {
            return list.subList(0, AI_QUERY_LIMIT);
        }
        return list;
    }

    /**
     * Query inventory risks with optional filters.
     * 对 AI 传入的参数做合法性校验，防止 AI 编造不存在的枚举值。
     */
    public List<InventoryRisk> queryRisks(String riskType, String riskLevel, String status, Integer limit) {
        // 校验 riskType
        String validRiskType = validateEnum(riskType, RiskType.class, "riskType");
        // 校验 riskLevel
        String validRiskLevel = validateEnum(riskLevel, RiskLevel.class, "riskLevel");
        // 校验 status
        String validStatus = validateEnum(status, RiskStatus.class, "status");

        int size = limit != null ? Math.min(limit, AI_QUERY_LIMIT) : AI_QUERY_LIMIT;
        PageQuery pq = new PageQuery();
        pq.setPageSize(size);
        return riskService.pageRisks(pq, null, null, validRiskType, validRiskLevel, validStatus).getRecords();
    }

    /**
     * 校验 AI 传入的枚举值是否合法，不合法则返回 null（忽略该过滤条件）。
     */
    private String validateEnum(String value, Class<? extends Enum<?>> enumClass, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        boolean valid = Arrays.stream(enumClass.getEnumConstants())
                .anyMatch(e -> e.name().equalsIgnoreCase(value));
        if (!valid) {
            log.warn("AI 传入非法 {} 值: '{}'，已忽略", fieldName, value);
            return null;
        }
        return value.toUpperCase();
    }

    /**
     * Query transfer suggestions (for AI to explain and present).
     */
    public List<TransferSuggestion> querySuggestions(Long skuId, Long warehouseId, String status, Integer limit) {
        int size = limit != null ? Math.min(limit, AI_QUERY_LIMIT) : AI_QUERY_LIMIT;
        PageQuery pq = new PageQuery();
        pq.setPageSize(size);
        return riskService.pageSuggestions(pq, skuId, status).getRecords();
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
     * List inventory flows for a SKU by business number (e.g. transferNo).
     */
    public List<InventoryFlow> listInventoryFlows(Long skuId, Long warehouseId, Integer limit) {
        List<InventoryFlow> flows = inventoryService.listFlowsByBizNo(null);
        // Filter by skuId and warehouseId if provided
        if (skuId != null || warehouseId != null) {
            flows = flows.stream()
                    .filter(f -> skuId == null || skuId.equals(f.getSkuId()))
                    .filter(f -> warehouseId == null || warehouseId.equals(f.getWarehouseId()))
                    .collect(Collectors.toList());
        }
        int size = limit != null ? Math.min(limit, AI_QUERY_LIMIT) : AI_QUERY_LIMIT;
        if (flows.size() > size) {
            return flows.subList(0, size);
        }
        return flows;
    }
}
