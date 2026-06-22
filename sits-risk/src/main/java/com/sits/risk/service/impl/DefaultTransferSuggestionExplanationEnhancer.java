package com.sits.risk.service.impl;

import com.sits.risk.dto.SuggestionExplainContext;
import com.sits.risk.service.TransferSuggestionExplanationEnhancer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认的调拨建议解释增强器 — 直接返回规则生成的基础reason，不调用 AI。
 */
@Component(TransferSuggestionExplanationEnhancer.DEFAULT_BEAN)
public class DefaultTransferSuggestionExplanationEnhancer implements TransferSuggestionExplanationEnhancer {

    @Override
    public String enhance(SuggestionExplainContext context) {
        if (context == null) {
            return "";
        }
        return StringUtils.hasText(context.getBaseReason()) ? context.getBaseReason() : "";
    }
}
