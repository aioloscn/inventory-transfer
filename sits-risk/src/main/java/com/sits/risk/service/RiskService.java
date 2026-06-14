package com.sits.risk.service;

import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.entity.MqConsumeRecord;
import com.sits.transfer.entity.TransferSuggestion;

import java.util.List;

/**
 * Risk and suggestion service.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Inventory risk scanning (shortage / overstock detection)</li>
 *   <li>Transfer suggestion generation with scoring</li>
 *   <li>Compensation task management</li>
 *   <li>MQ idempotency record management</li>
 * </ul>
 */
public interface RiskService {

    // ==================== Risk Scan ====================

    /**
     * Scan all inventory records and identify risks.
     * <p>Shortage: available_stock < safety_stock, or support_days < 7.
     * <p>Overstock: available_stock > max_stock * 0.8, and support_days > 30.
     *
     * @return list of newly created risk records
     */
    List<InventoryRisk> scanRisks();

    /**
     * Query risks with optional filters.
     */
    List<InventoryRisk> listRisks(Long skuId, Long warehouseId, String riskType, String status);

    /**
     * Update risk status.
     */
    void updateRiskStatus(Long riskId, String status);

    // ==================== Transfer Suggestions ====================

    /**
     * Generate transfer suggestions for all unresolved risks.
     * <p>For each shortage risk, find the best source warehouse with surplus.
     * Score is based on distance, cost, priority.
     *
     * @return list of generated suggestions
     */
    List<TransferSuggestion> generateSuggestions();

    /**
     * Query suggestions with optional filters.
     */
    List<TransferSuggestion> listSuggestions(Long skuId, String status);

    /**
     * Confirm a suggestion (mark as CONFIRMED).
     */
    void confirmSuggestion(Long suggestionId);

    /**
     * Reject a suggestion (mark as REJECTED).
     */
    void rejectSuggestion(Long suggestionId);

    /**
     * Mark a suggestion as converted to transfer order.
     */
    void markSuggestionConverted(Long suggestionId);

    // ==================== Compensation Tasks ====================

    /**
     * Create a compensation task for a failed operation.
     */
    CompensationTask createCompensationTask(String bizType, String bizNo, String errorMessage);

    /**
     * List pending/retrying compensation tasks due for retry.
     */
    List<CompensationTask> listPendingCompensationTasks(int limit);

    /**
     * Update compensation task after retry attempt.
     */
    void updateCompensationTask(Long taskId, String status, String errorMessage);

    // ==================== MQ Idempotency ====================

    /**
     * Check if an MQ event has already been consumed (idempotency guard).
     *
     * @return true if already consumed, false otherwise
     */
    boolean isEventConsumed(String eventId, String consumerGroup);

    /**
     * Record an MQ event as consumed.
     */
    void recordEventConsumed(String eventId, String consumerGroup,
                              String bizNo, String eventType);
}
