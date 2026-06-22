package com.sits.ai.provider;

import com.sits.ai.entity.AiModelConfig;
import org.springframework.ai.chat.model.ChatModel;

/**
 * AI 模型供应商客户端接口。
 *
 * <p>每种 AI 模型供应商实现此接口，封装不同的 ChatModel 创建逻辑。
 * 通过 Spring AI ChatModel 保留 Tool Calling 等高级功能。
 */
public interface AiModelProviderClient {

    /**
     * 获取供应商标识。
     */
    String getProvider();

    /**
     * 根据配置创建 Spring AI ChatModel。
     *
     * @param config 模型配置（含解密后的 API Key）
     * @return Spring AI ChatModel 实例
     */
    ChatModel createChatModel(AiModelConfig config);
}
