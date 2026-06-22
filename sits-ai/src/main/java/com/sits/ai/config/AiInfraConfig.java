package com.sits.ai.config;

import com.sits.ai.provider.AiModelClientFactory;
import com.sits.common.util.ApiKeyCryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块基础设施配置 — 加密工具、客户端工厂。
 */
@Configuration
public class AiInfraConfig {

    @Bean
    public ApiKeyCryptoUtil apiKeyCryptoUtil(
            @Value("${ai.crypto.secret:sits-ai-default-secret-change-me}") String secret) {
        return new ApiKeyCryptoUtil(secret);
    }

    @Bean
    public AiModelClientFactory aiModelClientFactory() {
        return new AiModelClientFactory();
    }
}
