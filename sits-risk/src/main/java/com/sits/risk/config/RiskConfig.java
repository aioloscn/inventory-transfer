package com.sits.risk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * sits-risk 模块配置类。
 */
@Configuration
@EnableConfigurationProperties(SuggestionAiExplanationProperties.class)
public class RiskConfig {
}
