package com.sits.risk.service;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
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
     * Query risks with optional filters (paginated).
     */
    PageResult<InventoryRisk> pageRisks(PageQuery pageQuery, Long skuId, Long warehouseId, String riskType, String riskLevel, String status);

    /**
     * Get risk by primary key.
     */
    InventoryRisk getRiskById(Long riskId);

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
     * Query suggestions with optional filters (paginated).
     */
    PageResult<TransferSuggestion> pageSuggestions(PageQuery pageQuery, Long skuId, String status);

    /**
     * 根据主键查询调拨建议详情，不存在则抛异常。
     */
    TransferSuggestion getSuggestionById(Long suggestionId);

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

    /**
     * 将已确认的调拨建议转为正式的调拨单。
     *
     * <p>会实际调用 {@code TransferOrderService.create()} 创建调拨单，
     * 然后将建议状态标记为 CONVERTED。
     *
     * @param suggestionId 调拨建议主键
     * @return 生成的调拨单号
     */
    String convertToTransferOrder(Long suggestionId);

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
