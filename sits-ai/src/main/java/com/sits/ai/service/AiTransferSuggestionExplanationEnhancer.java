package com.sits.ai.service;

import com.sits.ai.entity.AiModelConfig;
import com.sits.ai.provider.AiModelClientFactory;
import com.sits.risk.dto.SuggestionExplainContext;
import com.sits.risk.service.TransferSuggestionExplanationEnhancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 增强的调拨建议解释器。
 *
 * <p>使用当前活跃的 ChatClient（从数据库动态获取）调用大模型生成更易读的 reason 文案。
 * AI 只能增强 reason，不能修改调拨数量、仓库等核心字段。
 *
 * <p>当 AI 调用失败、超时或返回为空时，回退到基础 reason。</p>
 */
@Slf4j
@Component(TransferSuggestionExplanationEnhancer.AI_BEAN)
@RequiredArgsConstructor
public class AiTransferSuggestionExplanationEnhancer implements TransferSuggestionExplanationEnhancer {

    private static final String SYSTEM_PROMPT = """
            你是智能库存调拨系统的运营分析助手。
            你只能根据后端已经计算好的调拨建议生成中文说明。
            禁止修改 skuId、sourceWarehouseId、targetWarehouseId、suggestQuantity、score 等核心字段。
            禁止编造数据库中没有的数据。
            风险等级、风险类型、状态等术语必须使用系统枚举值，例如：HIGH、MEDIUM、LOW、SHORTAGE、NEW。
            如果数据不足，请明确说明"数据不足"。
            输出内容用于 transfer_suggestion.reason 字段，要求简洁、专业、可审计，不超过 300 字。
            只返回纯文本 reason，不要返回 JSON、Markdown 表格或代码块。""";

    private final AiModelConfigService modelConfigService;
    private final AiModelClientFactory clientFactory;

    /** 延迟加载的 ChatClient 缓存，避免循环中重复查询 DB 和重建对象 */
    private volatile ChatClient cachedChatClient;

    @Override
    public String enhance(SuggestionExplainContext context) {
        String baseReason = context == null ? "" : context.getBaseReason();

        try {
            ChatClient chatClient = getOrCreateChatClient();
            if (chatClient == null) {
                return baseReason;
            }

            String userPrompt = buildUserPrompt(context);
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            if (StringUtils.hasText(result)) {
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("AI enhance transfer suggestion reason failed, fallback to base reason. riskNo={}",
                    context == null ? null : context.getRiskNo(), e);
            // 连接超时等异常后清除缓存，下次重建（可能是 API Key 变更了）
            cachedChatClient = null;
        }
        return baseReason;
    }

    /**
     * 获取或创建 ChatClient，避免在循环中反复查 DB 和构建对象。
     * 缓存命中时直接返回，不查 DB。
     */
    private ChatClient getOrCreateChatClient() {
        if (cachedChatClient != null) {
            return cachedChatClient;
        }

        synchronized (this) {
            if (cachedChatClient != null) {
                return cachedChatClient;
            }
            try {
                AiModelConfig config = modelConfigService.getCurrentModelConfig();
                ChatModel chatModel = clientFactory.createChatModel(config);
                cachedChatClient = ChatClient.builder(chatModel).build();
                log.info("Created cached ChatClient for suggestion explanation: provider={}", config.getProvider());
            } catch (Exception e) {
                log.warn("Failed to create ChatClient for explanation enhance", e);
                return null;
            }
        }
        return cachedChatClient;
    }

    private String buildUserPrompt(SuggestionExplainContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下调拨建议数据，生成一段简洁专业的运营说明：\n\n");
        sb.append("基础原因：").append(ctx.getBaseReason()).append("\n");
        sb.append("风险编号：").append(ctx.getRiskNo()).append("\n");
        sb.append("SKU ID：").append(ctx.getSkuId()).append("\n");
        sb.append("风险等级：").append(ctx.getRiskLevel()).append("\n");
        sb.append("目标仓库：").append(ctx.getTargetWarehouseName()).append("\n");
        sb.append("目标仓可用库存：").append(ctx.getTargetAvailableStock()).append("\n");
        sb.append("目标仓安全库存：").append(ctx.getTargetSafetyStock()).append("\n");
        sb.append("目标仓在途库存：").append(ctx.getTargetInTransitStock()).append("\n");
        if (ctx.getAvgDailySales() != null) {
            sb.append("日均销量：").append(ctx.getAvgDailySales()).append("\n");
        }
        if (ctx.getSupportDays() != null) {
            sb.append("可支撑天数：").append(ctx.getSupportDays()).append("\n");
        }
        sb.append("源仓库：").append(ctx.getSourceWarehouseName()).append("\n");
        sb.append("源仓可用库存：").append(ctx.getSourceAvailableStock()).append("\n");
        sb.append("源仓安全库存：").append(ctx.getSourceSafetyStock()).append("\n");
        sb.append("源仓可调拨库存：").append(ctx.getSourceTransferableStock()).append("\n");
        sb.append("建议调拨数量：").append(ctx.getSuggestQuantity()).append("\n");
        if (ctx.getScore() != null) {
            sb.append("评分：").append(ctx.getScore()).append("\n");
        }
        sb.append("\n请生成不超过300字的中文说明。");
        return sb.toString();
    }
}
