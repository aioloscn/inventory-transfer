package com.sits.ai.service;

import com.sits.risk.dto.SuggestionExplainContext;
import com.sits.risk.service.TransferSuggestionExplanationEnhancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * AI 增强的调拨建议解释器。
 *
 * <p>使用 ChatClient 调用大模型生成更易读的 reason 文案。
 * AI 只能增强 reason，不能修改调拨数量、仓库等核心字段。
 *
 * <p>当 AI 调用失败、超时或返回为空时，回退到基础 reason。
 */
@Component
@Primary
@ConditionalOnBean(ChatClient.class)
public class AiTransferSuggestionExplanationEnhancer implements TransferSuggestionExplanationEnhancer {

    private static final Logger log = LoggerFactory.getLogger(AiTransferSuggestionExplanationEnhancer.class);

    private static final String SYSTEM_PROMPT = """
            你是智能库存调拨系统的运营分析助手。
            你只能根据后端已经计算好的调拨建议生成中文说明。
            禁止修改 skuId、sourceWarehouseId、targetWarehouseId、suggestQuantity、score 等核心字段。
            禁止编造数据库中没有的数据。
            如果数据不足，请明确说明"数据不足"。
            输出内容用于 transfer_suggestion.reason 字段，要求简洁、专业、可审计，不超过 300 字。
            只返回纯文本 reason，不要返回 JSON、Markdown 表格或代码块。""";

    private final ChatClient chatClient;

    public AiTransferSuggestionExplanationEnhancer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String enhance(SuggestionExplainContext context) {
        try {
            String userPrompt = buildUserPrompt(context);
            String result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            if (result != null && !result.isBlank()) {
                // 截断到 300 字
                if (result.length() > 300) {
                    result = result.substring(0, 300);
                }
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("AI explanation enhance failed for risk {}, fallback to base reason. Error: {}",
                    context.getRiskNo(), e.getMessage());
        }
        return context.getBaseReason();
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
