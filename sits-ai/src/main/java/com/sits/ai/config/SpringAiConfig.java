package com.sits.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Spring AI configuration that provides a {@link ChatClient.Builder} bean
 * when no AI provider (OpenAI, Ollama, etc.) is configured.
 *
 * <p>This stub allows the application to start in development mode without
 * a real AI backend. Once a real AI provider starter is added and configured,
 * its auto-configuration will override this bean.
 */
@Configuration
public class SpringAiConfig {

    /**
     * Provides a {@link ChatClient.Builder} backed by a stub {@link ChatModel}.
     * Uses {@link ConditionalOnMissingBean} so that a real provider's
     * auto-configuration takes precedence when available.
     */
    @Bean
    @ConditionalOnMissingBean
    public ChatClient.Builder chatClientBuilder() {
        return ChatClient.builder(new StubChatModel());
    }

    /**
     * A minimal stub {@link ChatModel} that returns a placeholder response,
     * allowing the application context to start without a configured AI provider.
     */
    static class StubChatModel implements ChatModel {

        private static final String PLACEHOLDER =
                "🤖 AI Copilot is running in stub mode. " +
                "Configure an AI provider (e.g. spring.ai.openai.*) in application.yml to enable real AI responses.";

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(PLACEHOLDER))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }
    }
}
