package com.sits.risk.service;

import com.sits.risk.config.SuggestionAiExplanationProperties;
import com.sits.risk.dto.GenerateSuggestionsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 调拨建议解释增强器选择器。
 *
 * <p>根据 application.yml 总开关和 request.enableAiExplanation 动态选择 Enhancer。
 * 后续可通过替换开关来源（如 Nacos / Apollo / DB）实现热更新，无需修改其他模块。
 */
@Component
@RequiredArgsConstructor
public class TransferSuggestionExplanationEnhancerSelector {

    private final Map<String, TransferSuggestionExplanationEnhancer> enhancerMap;

    private final SuggestionAiExplanationProperties properties;

    /**
     * 根据配置和请求参数动态选择 Enhancer。
     *
     * <p>选择规则：
     * <ol>
     *   <li>总开关 disabled → 永远走 Default</li>
     *   <li>总开关 enabled：默认走 AI（若 AI Bean 不存在则 fallback Default）；
     *       仅当 request.enableAiExplanation 显式传 false 时才走 Default</li>
     * </ol>
     */
    public TransferSuggestionExplanationEnhancer select(GenerateSuggestionsRequest request) {
        boolean useAi = properties.isEnabled()
                && (request == null || request.isAiEnabled());

        if (useAi) {
            TransferSuggestionExplanationEnhancer aiEnhancer =
                    enhancerMap.get(TransferSuggestionExplanationEnhancer.AI_BEAN);

            if (aiEnhancer != null) {
                return aiEnhancer;
            }
        }

        TransferSuggestionExplanationEnhancer defaultEnhancer =
                enhancerMap.get(TransferSuggestionExplanationEnhancer.DEFAULT_BEAN);

        if (defaultEnhancer == null) {
            throw new IllegalStateException("Default TransferSuggestionExplanationEnhancer not found");
        }

        return defaultEnhancer;
    }
}
