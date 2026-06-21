package com.sits.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sits.ai.service.AiReportService;
import com.sits.common.base.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;

/**
 * AI Copilot API — 聊天、日报生成、异常诊断。
 *
 * <p>AI 可自动调用以下只读工具获取数据：
 * <ul>
 *   <li>queryInventory — 查询 SKU 库存</li>
 *   <li>listWarehouseInventory — 列出仓库库存</li>
 *   <li>queryRisks — 查询库存风险</li>
 *   <li>querySuggestions — 查询调拨建议</li>
 *   <li>getTransferOrder — 查询调拨单详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiCopilotController {

    private final ChatClient chatClient;
    private final List<ToolCallback> toolCallbacks;
    private final AiReportService aiReportService;
    private final ObjectMapper objectMapper;

    public AiCopilotController(ChatClient chatClient,
                               List<ToolCallback> toolCallbacks,
                               AiReportService aiReportService,
                               ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.toolCallbacks = toolCallbacks;
        this.aiReportService = aiReportService;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式聊天接口 — SSE 方式逐 token 推送，事件统一 JSON 格式：
     * <pre>{@code
     *   data: {"type":"delta","content":"xxx"}
     *   data: {"type":"done"}
     * }</pre>
     * 注入系统提示词要求 AI 输出结构化 Markdown（表格/列表/标题）。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");

        return chatClient.prompt()
                .system(buildMarkdownSystemPrompt())
                .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .user(question)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .data("{\"type\":\"done\"}")
                                .build()));
    }

    /** 将 token 封装为 {"type":"delta","content":"..."} 的 ServerSentEvent */
    private ServerSentEvent<String> buildDeltaEvent(String token) {
        try {
            Map<String, String> event = new LinkedHashMap<>();
            event.put("type", "delta");
            event.put("content", token);
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(event))
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .data("{\"type\":\"delta\",\"content\":\"\"}")
                    .build();
        }
    }

    /**
     * 系统提示词：要求 AI 使用结构化 Markdown 输出，
     * 库存风险必须用表格，调拨建议用有序列表。
     */
    private String buildMarkdownSystemPrompt() {
        return """
                你是智能库存调拨系统的 AI Copilot。
                
                回答要求：
                1. 使用 Markdown 格式输出，不要输出 HTML。
                2. 不要输出无结构的大段纯文本，必须分节、分点。
                3. 库存风险分析必须优先使用 Markdown 表格。
                4. 每个 SKU 单独一行。
                5. 调拨建议使用有序列表，按优先级从高到低排列。
                6. 风险等级使用：高风险 / 中风险 / 低风险。
                7. 回答结构固定为：
                
                ## 库存风险概览
                用 2~3 句话总结当前风险情况。
                
                ## 风险明细
                使用 Markdown 表格，字段包括：
                | SKU | 仓库 | 风险类型 | 风险等级 | 当前库存 | 安全库存 | 建议处理 |
                |---|---|---|---|---|---|---|
                
                ## 调拨建议
                使用有序列表说明建议优先级，每条建议注明涉及的 SKU、仓库、数量。
                
                ## 注意事项
                说明是否需要人工确认、是否建议生成调拨单、是否存在数据不足。
                """;
    }

    /**
     * 生成运营日报 — 自动收集系统数据，交给 AI 生成日报（SSE 流式）。
     */
    @PostMapping(value = "/daily-report/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> dailyReportStream() {
        Map<String, Object> reportData = aiReportService.gatherReportData();
        String context = aiReportService.formatReportContext(reportData);

        String prompt = context + "\n\n"
                + "请根据以上数据，生成一份专业的「库存运营日报」（日期：" + LocalDate.now() + "）。要求：\n"
                + "1. 先概述今日整体运营概况（一句话总结）\n"
                + "2. 风险分析：解读风险分布，重点关注严重和高风险项\n"
                + "3. 调拨进度：分析调拨单各状态占比，指出异常（取消/失败）的调拨单\n"
                + "4. 行动建议：给出 2-3 条具体的运营建议\n"
                + "格式使用 Markdown，层次分明，数据准确。";

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .data("{\"type\":\"done\"}")
                                .build()));
    }

    /**
     * 异常诊断 — 针对指定调拨单，查询其详情和操作日志，交给 AI 诊断问题（SSE 流式）。
     */
    @PostMapping(value = "/diagnose/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> diagnoseStream(@RequestBody Map<String, String> request) {
        String transferNo = request.getOrDefault("transferNo", "");

        Map<String, Object> diagnoseData = aiReportService.gatherDiagnoseData(transferNo);

        if (diagnoseData.containsKey("error")) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .data("{\"type\":\"delta\",\"content\":\"诊断失败：" + diagnoseData.get("error") + "\"}")
                            .build(),
                    ServerSentEvent.<String>builder()
                            .data("{\"type\":\"done\"}")
                            .build());
        }

        String prompt = "请对以下调拨单进行异常诊断分析：\n\n" +
                "【调拨单信息】\n" +
                diagnoseData.get("transferOrder") + "\n\n" +
                "【操作日志】\n" +
                diagnoseData.get("logs") + "\n\n" +
                "【源仓库库存】" + diagnoseData.get("sourceInventory") + "\n" +
                "【目标仓库库存】" + diagnoseData.get("targetInventory") + "\n\n" +
                "请分析：\n" +
                "1. 该调拨单当前处于什么状态，是否正常\n" +
                "2. 流程是否有异常（如某步骤耗时异常、卡在某个状态过久）\n" +
                "3. 如果已取消或失败，可能的原因是什么\n" +
                "4. 给出处理建议\n" +
                "格式使用 Markdown，简洁明了。";

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .data("{\"type\":\"done\"}")
                                .build()));
    }

    @GetMapping("/dashboard-stats")
    public Result<Map<String, Object>> dashboardStats() {
        return Result.success(aiReportService.gatherDashboardStats());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("module", "ai-copilot");
        status.put("toolsCount", String.valueOf(toolCallbacks.size()));
        return status;
    }
}
