package com.sits.risk.service.impl;

import com.sits.risk.dto.SuggestionExplainContext;
import com.sits.risk.service.TransferSuggestionExplanationEnhancer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认的调拨建议解释增强器 — 直接返回规则生成的基础reason，不调用 AI。
 *
 * <p>当 sits-ai 模块未提供 AI 实现时，Spring 会使用此默认 Bean。
 * 当 sits-ai 模块提供了 {@code AiTransferSuggestionExplanationEnhancer} 时，此 Bean 会被覆盖。
 */
@Component
@ConditionalOnMissingBean(value = TransferSuggestionExplanationEnhancer.class, ignored = DefaultTransferSuggestionExplanationEnhancer.class)
public class DefaultTransferSuggestionExplanationEnhancer implements TransferSuggestionExplanationEnhancer {

    @Override
    public String enhance(SuggestionExplainContext context) {
        return context.getBaseReason();
    }
}
