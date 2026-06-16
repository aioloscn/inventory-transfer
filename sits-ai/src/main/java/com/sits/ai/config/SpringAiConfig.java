package com.sits.ai.config;

import com.sits.ai.tool.AiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Spring AI 配置 — 提供 ChatClient，当有真实 AI Provider 时自动注册工具函数。
 *
 * <p>当 DeepSeek/OpenAI 配置了 api-key 后，Spring AI 自动配置会创建
 * {@code ChatClient.Builder}，本配置中的 stub 会被跳过。
 * 同时 {@link #chatClient} 会将 {@link AiTools} 中的只读工具注册为
 * Function Calling 函数，使 AI 能查询实时业务数据。
 */
@Configuration
public class SpringAiConfig {

    /**
     * 创建工具增强的 ChatClient — 注入所有只读 FunctionCallback 函数。
     *
     * <p>AI 在回答时会根据需要自动调用这些函数获取数据，然后综合给出回答。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }

    // ==================== Function Callbacks ====================

    /** 查询指定 SKU 在指定仓库的库存 */
    @Bean
    public FunctionCallback queryInventoryCallback(AiTools tools) {
        return FunctionCallback.builder()
                .function("queryInventory", (QueryInventoryInput input) ->
                        tools.queryInventory(input.skuId, input.warehouseId))
                .description("查询指定 SKU 在指定仓库的库存信息，返回可用库存、锁定库存、在途库存等")
                .inputType(QueryInventoryInput.class)
                .build();
    }

    /** 列出指定仓库的全部库存 */
    @Bean
    public FunctionCallback listWarehouseInventoryCallback(AiTools tools) {
        return FunctionCallback.builder()
                .function("listWarehouseInventory", (ListWarehouseInventoryInput input) ->
                        tools.listWarehouseInventory(input.warehouseId))
                .description("列出指定仓库的全部 SKU 库存，用于了解仓库整体库存概况")
                .inputType(ListWarehouseInventoryInput.class)
                .build();
    }

    /** 查询库存风险 */
    @Bean
    public FunctionCallback queryRisksCallback(AiTools tools) {
        return FunctionCallback.builder()
                .function("queryRisks", (QueryRisksInput input) ->
                        tools.queryRisks(input.skuId, input.warehouseId, input.riskType))
                .description("查询库存风险列表，可按 SKU、仓库、风险类型筛选，返回风险等级、可支撑天数等")
                .inputType(QueryRisksInput.class)
                .build();
    }

    /** 查询调拨建议 */
    @Bean
    public FunctionCallback querySuggestionsCallback(AiTools tools) {
        return FunctionCallback.builder()
                .function("querySuggestions", (QuerySuggestionsInput input) ->
                        tools.querySuggestions(input.skuId, input.status))
                .description("查询调拨建议列表，可按 SKU 和状态筛选，返回建议的调拨方案、评分、原因等")
                .inputType(QuerySuggestionsInput.class)
                .build();
    }

    /** 查询调拨单详情 */
    @Bean
    public FunctionCallback getTransferOrderCallback(AiTools tools) {
        return FunctionCallback.builder()
                .function("getTransferOrder", (GetTransferOrderInput input) ->
                        tools.getTransferOrder(input.transferNo))
                .description("根据调拨单号查询调拨单详情，返回调拨单的状态、SKU、数量、仓库等信息")
                .inputType(GetTransferOrderInput.class)
                .build();
    }

    // ==================== Function Input Record Types ====================

    /** 查询库存输入 */
    public record QueryInventoryInput(Long skuId, Long warehouseId) {}

    /** 列出仓库库存输入 */
    public record ListWarehouseInventoryInput(Long warehouseId) {}

    /** 查询风险输入 */
    public record QueryRisksInput(Long skuId, Long warehouseId, String riskType) {}

    /** 查询建议输入 */
    public record QuerySuggestionsInput(Long skuId, String status) {}

    /** 查询调拨单输入 */
    public record GetTransferOrderInput(String transferNo) {}

    // ==================== Stub Fallback ====================

    /**
     * 当没有 AI Provider（如 DeepSeek/OpenAI）配置时，提供占位 ChatClient.Builder。
     *
     * <p>使用 {@link ConditionalOnMissingBean}，有真实 Provider 时自动跳过。
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder(new StubChatModel());
    }

    /**
     * 占位 ChatModel — 当没有配 AI Provider 时返回提示文本。
     */
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
