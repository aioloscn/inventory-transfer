package com.sits.ai.provider;

import com.sits.ai.entity.AiModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 火山方舟模型供应商客户端。
 *
 * <p>火山方舟 API v3 兼容 OpenAI 协议，通过 Spring AI OpenAiChatModel 实现。
 */
public class VolcengineArkModelProviderClient implements AiModelProviderClient {

    private static final Logger log = LoggerFactory.getLogger(VolcengineArkModelProviderClient.class);
    private static final String PROVIDER = "VOLCENGINE_ARK";

    /** HTTP 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** HTTP 读取超时 */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    /** 火山方舟 Chat Completions 路径，API v3 兼容 OpenAI 协议 */
    private static final String COMPLETIONS_PATH = "/api/v3/chat/completions";

    public VolcengineArkModelProviderClient() {
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public ChatModel createChatModel(AiModelConfig config) {
        String baseUrl = resolveBaseUrl(config);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(factory);
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(baseUrl)
                .completionsPath(COMPLETIONS_PATH)
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.getModelName())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .toolCallingManager(ToolCallingManager.builder().build())
                .build();
    }

    /**
     * 解析 baseUrl：去除末尾斜杠，默认使用火山方舟北京节点。
     * 注意：不要在此拼接 /api/v3，completionsPath 已包含完整路径。
     */
    private String resolveBaseUrl(AiModelConfig config) {
        if (config != null && config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            String url = config.getBaseUrl();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        return "https://ark.cn-beijing.volces.com";
    }
}
