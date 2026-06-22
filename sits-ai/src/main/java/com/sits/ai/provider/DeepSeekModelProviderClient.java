package com.sits.ai.provider;

import com.sits.ai.entity.AiModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * DeepSeek 模型供应商客户端。
 *
 * <p>基于 OpenAI 兼容协议，通过 Spring AI OpenAiChatModel 实现。
 */
public class DeepSeekModelProviderClient implements AiModelProviderClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekModelProviderClient.class);
    private static final String PROVIDER = "DEEPSEEK";

    /** HTTP 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** HTTP 读取超时（流式场景用较长的超时，同步 call 场景由上层控制） */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private final WebClient webClient;

    public DeepSeekModelProviderClient() {
        this.webClient = WebClient.builder().build();
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
     * 查询 DeepSeek 账户余额。
     *
     * @param apiKey 解密后的 API Key
     * @return 余额信息 JSON 字符串
     */
    public String queryBalance(String apiKey) {
        String url = resolveBaseUrl(null) + "/user/balance";

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String resolveBaseUrl(AiModelConfig config) {
        if (config != null && config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            String url = config.getBaseUrl();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        return "https://api.deepseek.com";
    }
}
