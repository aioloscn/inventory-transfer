package com.sits.ai.config;

import com.sits.ai.tool.AiTools;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 — 注册只读工具函数。
 *
 * <p>ChatClient 现在通过 {@link AiInfraConfig} 和 {@code AiModelClientFactory}
 * 动态创建，根据数据库中的当前模型配置（provider、apiKey、baseUrl）路由到
 * 对应的大模型供应商（DeepSeek / 火山方舟）。
 *
 * <p>所有工具函数均为只读，AI 不能直接写 SQL 或修改数据库。
 */
@Configuration
public class SpringAiConfig {

    // ==================== Tool Callbacks ====================

    @Bean
    public ToolCallback queryInventoryCallback(AiTools tools) {
        return FunctionToolCallback.builder("queryInventory",
                        (QueryInventoryInput input) ->
                                tools.queryInventory(input.skuId, input.warehouseId))
                .description("查询指定 SKU 在指定仓库的库存信息，返回可用库存、锁定库存、在途库存等。此工具为只读。")
                .inputType(QueryInventoryInput.class)
                .build();
    }

    @Bean
    public ToolCallback listWarehouseInventoryCallback(AiTools tools) {
        return FunctionToolCallback.builder("listWarehouseInventory",
                        (ListWarehouseInventoryInput input) ->
                                tools.listWarehouseInventory(input.warehouseId, input.limit))
                .description("列出指定仓库的全部 SKU 库存，用于了解仓库整体库存概况。此工具为只读。")
                .inputType(ListWarehouseInventoryInput.class)
                .build();
    }

    @Bean
    public ToolCallback queryRisksCallback(AiTools tools) {
        return FunctionToolCallback.builder("queryRisks",
                        (QueryRisksInput input) ->
                                tools.queryRisks(input.riskType, input.riskLevel, input.status, input.limit))
                .description("查询库存风险列表，可按风险类型（SHORTAGE/OVERSTOCK）、风险等级（HIGH/MEDIUM/LOW）、状态筛选，返回风险等级、可支撑天数等。此工具为只读。")
                .inputType(QueryRisksInput.class)
                .build();
    }

    @Bean
    public ToolCallback querySuggestionsCallback(AiTools tools) {
        return FunctionToolCallback.builder("querySuggestions",
                        (QuerySuggestionsInput input) ->
                                tools.querySuggestions(input.skuId, input.warehouseId, input.status, input.limit))
                .description("查询调拨建议列表，可按 SKU、仓库、状态筛选，返回建议的调拨方案、评分、原因等。此工具为只读。")
                .inputType(QuerySuggestionsInput.class)
                .build();
    }

    @Bean
    public ToolCallback getTransferOrderCallback(AiTools tools) {
        return FunctionToolCallback.builder("getTransferOrder",
                        (GetTransferOrderInput input) ->
                                tools.getTransferOrder(input.transferNo))
                .description("根据调拨单号查询调拨单详情，返回调拨单的状态、SKU、数量、源仓库、目标仓库等信息。此工具为只读。")
                .inputType(GetTransferOrderInput.class)
                .build();
    }

    @Bean
    public ToolCallback listTransferLogsCallback(AiTools tools) {
        return FunctionToolCallback.builder("listTransferLogs",
                        (ListTransferLogsInput input) ->
                                tools.listTransferLogs(input.transferNo))
                .description("查询指定调拨单的操作日志，返回各步骤的操作时间、操作人、操作类型、状态变更等。此工具为只读。")
                .inputType(ListTransferLogsInput.class)
                .build();
    }

    @Bean
    public ToolCallback listInventoryFlowsCallback(AiTools tools) {
        return FunctionToolCallback.builder("listInventoryFlows",
                        (ListInventoryFlowsInput input) ->
                                tools.listInventoryFlows(input.skuId, input.warehouseId, input.limit))
                .description("查询指定 SKU 的库存流水记录，可选的 warehouseId 用于过滤仓库，返回库存变更的时间、类型、数量等。此工具为只读。")
                .inputType(ListInventoryFlowsInput.class)
                .build();
    }

    // ==================== Tool Input Record Types ====================

    public record QueryInventoryInput(Long skuId, Long warehouseId) {}
    public record ListWarehouseInventoryInput(Long warehouseId, Integer limit) {}
    public record QueryRisksInput(String riskType, String riskLevel, String status, Integer limit) {}
    public record QuerySuggestionsInput(Long skuId, Long warehouseId, String status, Integer limit) {}
    public record GetTransferOrderInput(String transferNo) {}
    public record ListTransferLogsInput(String transferNo) {}
    public record ListInventoryFlowsInput(Long skuId, Long warehouseId, Integer limit) {}
}
