package com.sits.ai.provider;

import com.sits.ai.entity.AiModelConfig;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型客户端工厂 — 根据 provider 路由到对应实现，创建 ChatModel。
 */
public class AiModelClientFactory {

    private final Map<String, AiModelProviderClient> clients = new ConcurrentHashMap<>();

    public AiModelClientFactory() {
        DeepSeekModelProviderClient deepSeekClient = new DeepSeekModelProviderClient();
        VolcengineArkModelProviderClient volcengineClient = new VolcengineArkModelProviderClient();
        clients.put(deepSeekClient.getProvider(), deepSeekClient);
        clients.put(volcengineClient.getProvider(), volcengineClient);
    }

    /**
     * 根据配置创建 ChatModel（自动适配 provider）。
     *
     * @param config 模型配置（含解密后的 API Key）
     * @return Spring AI ChatModel
     */
    public ChatModel createChatModel(AiModelConfig config) {
        AiModelProviderClient client = clients.get(config.getProvider());
        if (client == null) {
            throw new IllegalArgumentException("不支持的 AI 模型供应商: " + config.getProvider());
        }
        return client.createChatModel(config);
    }

    /**
     * 获取指定 provider 的客户端。
     */
    public AiModelProviderClient getClient(String provider) {
        AiModelProviderClient client = clients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("不支持的 AI 模型供应商: " + provider);
        }
        return client;
    }

    /**
     * 获取 DeepSeek 客户端（用于额度查询等特殊操作）。
     */
    public DeepSeekModelProviderClient getDeepSeekClient() {
        return (DeepSeekModelProviderClient) clients.get("DEEPSEEK");
    }
}
