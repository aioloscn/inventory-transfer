package com.sits.risk.service;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.risk.dto.RiskScanResult;
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.transfer.entity.TransferSuggestion;

import java.util.List;

/**
 * 风险与调拨建议服务。
 */
public interface RiskService {

    // ==================== 风险扫描 ====================

    /**
     * 扫描所有库存记录，识别风险（游标分页）。
     */
    RiskScanResult scanRisks();

    /**
     * 分页查询风险记录，支持多条件筛选。
     */
    PageResult<InventoryRisk> pageRisks(PageQuery pageQuery, Long skuId, Long warehouseId, String riskType, String riskLevel, String status);

    /**
     * 根据主键查询风险记录。
     */
    InventoryRisk getRiskById(Long riskId);

    /**
     * 更新风险状态。
     */
    void updateRiskStatus(Long riskId, String status);

    /**
     * 对指定 SKU + 仓库进行局部风险重扫。
     * <p>库存变更（锁定/出库/入库）后触发，评估缺货和积压风险。
     * <p>会先将该 SKU+仓库已有的未解决风险标记为 RESOLVED，再创建新风险记录。
     */
    void rescanRisk(Long skuId, Long warehouseId);

    /**
     * 处理一批 SKU 的风险扫描（内部方法，每批独立事务）。
     * 批量查库存 + 销量 → 内存计算风险 → 批量 upsert。
     */
    int processBatch(List<Long> skuIds, String scanBatchNo, int lookbackDays);

    /**
     * 记录仓库事件通知（当前写日志，可扩展为推送通知/Webhook）。
     */
    void logEventNotification(String eventType, Long skuId, Long warehouseId, int quantity);

    // ==================== 调拨建议 ====================

    /**
     * 为所有未解决的风险生成调拨建议。
     * <p>对每个缺货风险，找到最优的有盈余的源仓库。
     * 评分基于距离、成本、优先级。
     *
     * @return 生成的建议列表
     */
    List<TransferSuggestion> generateSuggestions();

    /**
     * 分页查询调拨建议，支持多条件筛选。
     */
    PageResult<TransferSuggestion> pageSuggestions(PageQuery pageQuery, Long skuId, String status);

    /**
     * 根据主键查询调拨建议详情，不存在则抛异常。
     */
    TransferSuggestion getSuggestionById(Long suggestionId);

    /**
     * 确认调拨建议（标记为已确认）。
     */
    void confirmSuggestion(Long suggestionId);

    /**
     * 拒绝调拨建议（标记为已拒绝）。
     */
    void rejectSuggestion(Long suggestionId);

    /**
     * 标记调拨建议已转为调拨单。
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

    // ==================== 补偿任务 ====================

    /**
     * 为失败的操作创建补偿任务。
     */
    CompensationTask createCompensationTask(String bizType, String bizNo, String errorMessage);

    /**
     * 查询待重试的补偿任务列表。
     */
    List<CompensationTask> listPendingCompensationTasks(int limit);

    /**
     * 更新补偿任务的重试结果。
     */
    void updateCompensationTask(Long taskId, String status, String errorMessage);

    // ==================== MQ 幂等 ====================

    /**
     * 检查 MQ 事件是否已被消费（幂等防护）。
     *
     * @return true 表示已消费，false 表示未消费
     */
    boolean isEventConsumed(String eventId, String consumerGroup);

    /**
     * 记录 MQ 事件已被消费。
     */
    void recordEventConsumed(String eventId, String consumerGroup,
                              String bizNo, String eventType);
}
