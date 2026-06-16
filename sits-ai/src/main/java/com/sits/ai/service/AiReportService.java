package com.sits.ai.service;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.service.InventoryService;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.service.RiskService;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferOrderLog;
import com.sits.transfer.entity.TransferSuggestion;
import com.sits.transfer.service.TransferOrderService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 运营日报与异常诊断服务 — 收集系统运行数据，拼接成结构化上下文送给 AI 分析。
 *
 * <p>日报数据包括：风险统计、调拨单状态分布、调拨建议统计、库存总览。
 * <p>异常诊断：根据调拨单号查询调拨单及其操作日志，交给 AI 诊断问题。
 */
@Service
public class AiReportService {

    private static final int REPORT_QUERY_LIMIT = 500;

    private final RiskService riskService;
    private final TransferOrderService transferOrderService;
    private final InventoryService inventoryService;

    public AiReportService(RiskService riskService,
                           TransferOrderService transferOrderService,
                           InventoryService inventoryService) {
        this.riskService = riskService;
        this.transferOrderService = transferOrderService;
        this.inventoryService = inventoryService;
    }

    /**
     * 收集日报所需的全部系统数据，返回结构化 Map 供 AI 分析。
     */
    public Map<String, Object> gatherReportData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 风险统计
        PageQuery pq = new PageQuery();
        pq.setPageSize(REPORT_QUERY_LIMIT);
        List<InventoryRisk> allRisks = riskService.pageRisks(pq, null, null, null, null, null).getRecords();
        data.put("riskSummary", buildRiskSummary(allRisks));

        // 调拨单统计
        PageResult<TransferOrder> orderPage = transferOrderService.page(pq);
        List<TransferOrder> allOrders = orderPage.getRecords();
        data.put("transferOrderSummary", buildTransferOrderSummary(allOrders));

        // 调拨建议统计
        List<TransferSuggestion> allSuggestions = riskService.pageSuggestions(pq, null, null).getRecords();
        data.put("suggestionSummary", buildSuggestionSummary(allSuggestions));

        // 待处理补偿任务
        data.put("pendingCompensationTasks", riskService.listPendingCompensationTasks(50).size());

        return data;
    }

    /**
     * 收集指定调拨单的诊断数据（调拨单详情 + 操作日志）。
     */
    public Map<String, Object> gatherDiagnoseData(String transferNo) {
        Map<String, Object> data = new LinkedHashMap<>();

        TransferOrder order = transferOrderService.getByTransferNo(transferNo);
        if (order == null) {
            data.put("error", "调拨单不存在: " + transferNo);
            return data;
        }
        data.put("transferOrder", order);

        List<TransferOrderLog> logs = transferOrderService.listLogs(transferNo);
        data.put("logs", logs);
        data.put("logCount", logs.size());

        // 同时查询源仓库和目标仓库的库存
        WarehouseInventory sourceInv = inventoryService.getBySkuAndWarehouse(order.getSkuId(), order.getSourceWarehouseId());
        WarehouseInventory targetInv = inventoryService.getBySkuAndWarehouse(order.getSkuId(), order.getTargetWarehouseId());
        data.put("sourceInventory", sourceInv);
        data.put("targetInventory", targetInv);

        return data;
    }

    /**
     * 将日报数据格式化为人类可读的文本摘要，作为 AI 的上下文提示。
     */
    public String formatReportContext(Map<String, Object> reportData) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是智能库存调拨系统当前运行数据：\n\n");

        // 风险统计
        @SuppressWarnings("unchecked")
        Map<String, Object> riskSummary = (Map<String, Object>) reportData.get("riskSummary");
        sb.append("【风险统计】\n");
        sb.append("  风险总数: ").append(riskSummary.get("total")).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> riskByType = (Map<String, Long>) riskSummary.get("byType");
        sb.append("  按类型: ").append(riskByType).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> riskByLevel = (Map<String, Long>) riskSummary.get("byLevel");
        sb.append("  按等级: ").append(riskByLevel).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> riskByStatus = (Map<String, Long>) riskSummary.get("byStatus");
        sb.append("  按状态: ").append(riskByStatus).append("\n\n");

        // 调拨单统计
        @SuppressWarnings("unchecked")
        Map<String, Object> orderSummary = (Map<String, Object>) reportData.get("transferOrderSummary");
        sb.append("【调拨单统计】\n");
        sb.append("  调拨单总数: ").append(orderSummary.get("total")).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> orderByStatus = (Map<String, Long>) orderSummary.get("byStatus");
        sb.append("  按状态: ").append(orderByStatus).append("\n\n");

        // 调拨建议统计
        @SuppressWarnings("unchecked")
        Map<String, Object> sugSummary = (Map<String, Object>) reportData.get("suggestionSummary");
        sb.append("【调拨建议统计】\n");
        sb.append("  建议总数: ").append(sugSummary.get("total")).append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Long> sugByStatus = (Map<String, Long>) sugSummary.get("byStatus");
        sb.append("  按状态: ").append(sugByStatus).append("\n\n");

        // 补偿任务
        sb.append("【补偿任务】\n");
        sb.append("  待处理补偿任务: ").append(reportData.get("pendingCompensationTasks")).append(" 个\n");

        return sb.toString();
    }

    /**
     * 构建风险统计数据。
     */
    private Map<String, Object> buildRiskSummary(List<InventoryRisk> risks) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", risks.size());
        summary.put("byType", risks.stream()
                .collect(Collectors.groupingBy(r -> nvl(r.getRiskType(), "未知"), Collectors.counting())));
        summary.put("byLevel", risks.stream()
                .collect(Collectors.groupingBy(r -> nvl(r.getRiskLevel(), "未知"), Collectors.counting())));
        summary.put("byStatus", risks.stream()
                .collect(Collectors.groupingBy(r -> nvl(r.getStatus(), "未知"), Collectors.counting())));

        // 列出高风险和严重的风险项摘要
        List<Map<String, Object>> criticalRisks = risks.stream()
                .filter(r -> "critical".equalsIgnoreCase(r.getRiskLevel()) || "high".equalsIgnoreCase(r.getRiskLevel()))
                .limit(20)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("riskNo", r.getRiskNo());
                    m.put("skuId", r.getSkuId());
                    m.put("warehouseId", r.getWarehouseId());
                    m.put("riskType", r.getRiskType());
                    m.put("riskLevel", r.getRiskLevel());
                    m.put("supportDays", r.getSupportDays());
                    return m;
                })
                .collect(Collectors.toList());
        summary.put("criticalAndHighRisks", criticalRisks);

        return summary;
    }

    /**
     * 构建调拨单状态分布统计。
     */
    private Map<String, Object> buildTransferOrderSummary(List<TransferOrder> orders) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", orders.size());
        summary.put("byStatus", orders.stream()
                .collect(Collectors.groupingBy(o -> nvl(o.getStatus(), "未知"), Collectors.counting())));

        // 异常状态（CANCELLED、FAILED）的调拨单列表
        List<String> abnormalOrders = orders.stream()
                .filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus()) || "FAILED".equalsIgnoreCase(o.getStatus()))
                .limit(20)
                .map(TransferOrder::getTransferNo)
                .collect(Collectors.toList());
        summary.put("abnormalTransferNos", abnormalOrders);

        return summary;
    }

    /**
     * 构建调拨建议状态分布统计。
     */
    private Map<String, Object> buildSuggestionSummary(List<TransferSuggestion> suggestions) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", suggestions.size());
        summary.put("byStatus", suggestions.stream()
                .collect(Collectors.groupingBy(s -> nvl(s.getStatus(), "未知"), Collectors.counting())));
        return summary;
    }

    /**
     * 收集仪表盘所需的统计数据，返回扁平结构供前端图表直接使用。
     *
     * <p>包含：四大统计卡片数值 + 风险按类型/等级分布 + 调拨单状态分布 + 最近风险动态。
     */
    public Map<String, Object> gatherDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        PageQuery pq = new PageQuery();
        pq.setPageSize(REPORT_QUERY_LIMIT);

        // 风险统计
        List<InventoryRisk> allRisks = riskService.pageRisks(pq, null, null, null, null, null).getRecords();
        stats.put("riskCount", allRisks.size());
        stats.put("riskByType", allRisks.stream()
                .collect(Collectors.groupingBy(r -> nvl(r.getRiskType(), "未知"), Collectors.counting())));
        stats.put("riskByLevel", allRisks.stream()
                .collect(Collectors.groupingBy(r -> nvl(r.getRiskLevel(), "未知"), Collectors.counting())));

        // 调拨单统计
        List<TransferOrder> allOrders = transferOrderService.page(pq).getRecords();
        stats.put("transferCount", allOrders.size());
        stats.put("transferByStatus", allOrders.stream()
                .collect(Collectors.groupingBy(o -> nvl(o.getStatus(), "未知"), Collectors.counting())));
        // 待审批数量 = APPROVING 状态的调拨单数
        long pendingCount = allOrders.stream()
                .filter(o -> "APPROVING".equalsIgnoreCase(o.getStatus()))
                .count();
        stats.put("pendingApproval", pendingCount);

        // 调拨建议统计
        List<TransferSuggestion> allSuggestions = riskService.pageSuggestions(pq, null, null).getRecords();
        stats.put("suggestionCount", allSuggestions.size());

        // 最近 5 条风险动态
        List<InventoryRisk> recentRisks = allRisks.stream()
                .sorted(Comparator.comparing(InventoryRisk::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList());
        stats.put("recentRisks", recentRisks);

        // 补偿任务数
        stats.put("pendingCompensationTasks", riskService.listPendingCompensationTasks(50).size());

        return stats;
    }

    /** null 安全取值 */
    private String nvl(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
