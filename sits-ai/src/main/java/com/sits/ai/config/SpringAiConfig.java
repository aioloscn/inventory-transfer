package com.sits.ai.config;

import com.sits.ai.tool.AiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Spring AI 配置 — 提供 ChatClient，当有真实 AI Provider 时自动注册工具函数。
 *
 * <p>当 DeepSeek/OpenAI 配置了 api-key 后，Spring AI 自动配置会创建
 * {@code ChatClient.Builder}，此时优先创建真实的 {@code ChatClient}。
 * 若未配置提供商，则退化为 stub {@code ChatClient}，返回明确提示信息。
 * 同时 {@link #chatClient} 会将 {@link AiTools} 中的只读工具注册为
 * Tool Calling 工具，使 AI 能查询实时业务数据。
 */
@Configuration
public class SpringAiConfig {

    /**
     * 创建 ChatClient，优先使用 Spring AI 自动配置的 ChatModel（如 OpenAI/DeepSeek），
     * 未配置时退化为 stub，避免因条件注解执行顺序导致真实 AI 模型被覆盖。
     */
    @Bean
    public ChatClient chatClient(ObjectProvider<ChatModel> chatModelProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            return ChatClient.builder(chatModel).build();
        }
        return ChatClient.builder(new StubChatModel()).build();
    }

    // ==================== Tool Callbacks ====================

    @Bean
    public ToolCallback queryInventoryCallback(AiTools tools) {
        return FunctionToolCallback.builder("queryInventory",
                        (QueryInventoryInput input) ->
                                tools.queryInventory(input.skuId, input.warehouseId))
                .description("查询指定 SKU 在指定仓库的库存信息，返回可用库存、锁定库存、在途库存等")
                .inputType(QueryInventoryInput.class)
                .build();
    }

    @Bean
    public ToolCallback listWarehouseInventoryCallback(AiTools tools) {
        return FunctionToolCallback.builder("listWarehouseInventory",
                        (ListWarehouseInventoryInput input) ->
                                tools.listWarehouseInventory(input.warehouseId))
                .description("列出指定仓库的全部 SKU 库存，用于了解仓库整体库存概况")
                .inputType(ListWarehouseInventoryInput.class)
                .build();
    }

    @Bean
    public ToolCallback queryRisksCallback(AiTools tools) {
        return FunctionToolCallback.builder("queryRisks",
                        (QueryRisksInput input) ->
                                tools.queryRisks(input.skuId, input.warehouseId, input.riskType))
                .description("查询库存风险列表，可按 SKU、仓库、风险类型筛选，返回风险等级、可支撑天数等")
                .inputType(QueryRisksInput.class)
                .build();
    }

    @Bean
    public ToolCallback querySuggestionsCallback(AiTools tools) {
        return FunctionToolCallback.builder("querySuggestions",
                        (QuerySuggestionsInput input) ->
                                tools.querySuggestions(input.skuId, input.status))
                .description("查询调拨建议列表，可按 SKU 和状态筛选，返回建议的调拨方案、评分、原因等")
                .inputType(QuerySuggestionsInput.class)
                .build();
    }

    @Bean
    public ToolCallback getTransferOrderCallback(AiTools tools) {
        return FunctionToolCallback.builder("getTransferOrder",
                        (GetTransferOrderInput input) ->
                                tools.getTransferOrder(input.transferNo))
                .description("根据调拨单号查询调拨单详情，返回调拨单的状态、SKU、数量、仓库等信息")
                .inputType(GetTransferOrderInput.class)
                .build();
    }

    // ==================== Tool Input Record Types ====================

    public record QueryInventoryInput(Long skuId, Long warehouseId) {}
    public record ListWarehouseInventoryInput(Long warehouseId) {}
    public record QueryRisksInput(Long skuId, Long warehouseId, String riskType) {}
    public record QuerySuggestionsInput(Long skuId, String status) {}
    public record GetTransferOrderInput(String transferNo) {}

    // ==================== Stub (inner class, used in chatClient when no ChatModel available) ====================

    static class StubChatModel implements ChatModel {
        private static final String PLACEHOLDER =
                "AI Copilot 未配置 AI 提供商。请在 application.yml 中配置 spring.ai.openai.* 以启用真实 AI 回答。";

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(PLACEHOLDER))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
