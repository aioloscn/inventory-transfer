package com.sits.ai.controller;

import com.sits.ai.tool.AiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Copilot chat API.
 *
 * <p>Provides a chat endpoint that uses Spring AI with function calling
 * to let users ask questions about inventory, risks, and transfer orders.
 *
 * <p>The AI uses {@link AiTools} read-only functions to gather data,
 * then synthesizes answers in natural language.
 */
@RestController
@RequestMapping("/api/ai")
public class AiCopilotController {

    private final ChatClient chatClient;
    private final AiTools aiTools;

    public AiCopilotController(ChatClient.Builder chatClientBuilder, AiTools aiTools) {
        this.chatClient = chatClientBuilder.build();
        this.aiTools = aiTools;
    }

    /**
     * Chat — ask questions about inventory, risks, suggestions, transfers.
     *
     * <p>Example questions:
     * <ul>
     *   <li>"Which warehouses have shortage risks for SKU 1001?"</li>
     *   <li>"Show me the transfer order TRF20240101ABC12345 status"</li>
     *   <li>"What are the current transfer suggestions?"</li>
     * </ul>
     */
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");

        String answer = chatClient.prompt()
                .user(question)
                .call()
                .content();

        return Map.of("question", question, "answer", answer != null ? answer : "No answer");
    }

    /**
     * Health check for AI module.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "module", "ai-copilot");
    }
}
