package com.sits.risk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调拨建议 AI 原因增强配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sits.ai.suggestion.explanation")
public class SuggestionAiExplanationProperties {

    /**
     * 是否允许调拨建议原因使用 AI 增强。
     */
    private boolean enabled = false;
}
