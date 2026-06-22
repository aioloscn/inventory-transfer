package com.sits.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sits.ai.dto.AiQuotaVO;
import com.sits.ai.entity.AiModelConfig;
import com.sits.ai.provider.AiModelClientFactory;
import com.sits.ai.service.AiModelConfigService;
import com.sits.ai.service.AiReportService;
import com.sits.common.base.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
 * <p>每次调用时从数据库读取当前启用的模型配置，动态创建 ChatClient，
 * 支持 DeepSeek / 火山方舟等供应商切换。
 * AI 可自动调用以下只读工具获取数据：
 * <ul>
 *   <li>queryInventory — 查询 SKU 库存</li>
 *   <li>listWarehouseInventory — 列出仓库库存</li>
 *   <li>queryRisks — 查询库存风险</li>
 *   <li>querySuggestions — 查询调拨建议</li>
 *   <li>getTransferOrder — 查询调拨单详情</li>
 *   <li>listTransferLogs — 查询调拨单操作日志</li>
 *   <li>listInventoryFlows — 查询库存流水</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiCopilotController {

    private static final Logger log = LoggerFactory.getLogger(AiCopilotController.class);

    private final AiModelConfigService modelConfigService;
    private final AiModelClientFactory clientFactory;
    private final List<ToolCallback> toolCallbacks;
    private final AiReportService aiReportService;
    private final ObjectMapper objectMapper;

    public AiCopilotController(AiModelConfigService modelConfigService,
                               AiModelClientFactory clientFactory,
                               List<ToolCallback> toolCallbacks,
                               AiReportService aiReportService,
                               ObjectMapper objectMapper) {
        this.modelConfigService = modelConfigService;
        this.clientFactory = clientFactory;
        this.toolCallbacks = toolCallbacks;
        this.aiReportService = aiReportService;
        this.objectMapper = objectMapper;
    }

    // ==================== 意图拦截关键词 ====================

    private static final Set<String> ALLOWED_KEYWORDS = Set.of(
            "库存", "SKU", "仓库", "缺货", "积压", "风险", "调拨", "建议",
            "调拨单", "审批", "库存流水", "补偿", "锁定库存", "释放库存",
            "在途库存", "安全库存", "销量", "日均销量", "可支撑天数", "库存周转",
            "日报", "诊断", "运营", "异常"
    );

    private static final String REJECT_MESSAGE = "当前 AI Copilot 只支持智能库存调拨相关问题。"
            + "你可以询问库存风险、仓库库存、调拨建议、调拨单状态或库存流水。";

    // ==================== 流式聊天 ====================

    /**
     * 流式聊天接口 — SSE 方式逐 token 推送。
     */
    @PostMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");

        if (!isDomainRelated(question)) {
            return Flux.just(buildDeltaEvent(REJECT_MESSAGE), buildDoneEvent());
        }

        AiModelConfig config;
        try {
            config = modelConfigService.getCurrentModelConfig();
        } catch (Exception e) {
            log.warn("Failed to get current model config for chat", e);
            return Flux.just(buildErrorEvent("AI 模型配置错误：" + e.getMessage()), buildDoneEvent());
        }

        ChatClient chatClient = buildChatClient(config);

        return chatClient.prompt()
                .system(buildSystemPrompt())
                .toolCallbacks(toolCallbacks.toArray(new ToolCallback[0]))
                .user(question)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(buildDoneEvent()))
                .doOnError(e -> log.error("AI chat stream error", e));
    }

    /**
     * 生成运营日报 — 自动收集系统数据，交给 AI 生成日报（SSE 流式）。
     */
    @PostMapping(value = "/stream/daily-report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> dailyReportStream() {
        AiModelConfig config;
        try {
            config = modelConfigService.getCurrentModelConfig();
        } catch (Exception e) {
            log.warn("Failed to get current model config for daily report", e);
            return Flux.just(buildErrorEvent("AI 模型配置错误：" + e.getMessage()), buildDoneEvent());
        }

        Map<String, Object> reportData = aiReportService.gatherReportData();
        String context = aiReportService.formatReportContext(reportData);

        String prompt = context + "\n\n"
                + "请根据以上数据，生成一份专业的「库存运营日报」（日期：" + LocalDate.now() + "）。要求：\n"
                + "1. 先概述今日整体运营概况（一句话总结）\n"
                + "2. 风险分析：解读风险分布，重点关注严重和高风险项\n"
                + "3. 调拨进度：分析调拨单各状态占比，指出异常（取消/失败）的调拨单\n"
                + "4. 行动建议：给出 2-3 条具体的运营建议\n"
                + "格式使用 Markdown，层次分明，数据准确。";

        ChatClient chatClient = buildChatClient(config);

        return chatClient.prompt()
                .system(buildSystemPrompt())
                .user(prompt)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(buildDoneEvent()))
                .doOnError(e -> log.error("AI daily report stream error", e));
    }

    /**
     * 异常诊断 — 针对指定调拨单，交给 AI 诊断问题（SSE 流式）。
     */
    @PostMapping(value = "/stream/diagnose", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> diagnoseStream(@RequestBody Map<String, String> request) {
        String transferNo = request.getOrDefault("transferNo", "");

        AiModelConfig config;
        try {
            config = modelConfigService.getCurrentModelConfig();
        } catch (Exception e) {
            log.warn("Failed to get current model config for diagnose", e);
            return Flux.just(buildErrorEvent("AI 模型配置错误：" + e.getMessage()), buildDoneEvent());
        }

        Map<String, Object> diagnoseData = aiReportService.gatherDiagnoseData(transferNo);

        if (diagnoseData.containsKey("error")) {
            return Flux.just(
                    buildDeltaEvent("诊断失败：" + diagnoseData.get("error")),
                    buildDoneEvent());
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

        ChatClient chatClient = buildChatClient(config);

        return chatClient.prompt()
                .system(buildSystemPrompt())
                .user(prompt)
                .stream()
                .content()
                .map(this::buildDeltaEvent)
                .concatWith(Mono.just(buildDoneEvent()))
                .doOnError(e -> log.error("AI diagnose stream error", e));
    }

    // ---- 额度查询 ----

    @GetMapping("/quota/deepseek")
    public Result<AiQuotaVO> deepSeekQuota() {
        return Result.success(modelConfigService.getDeepSeekQuota());
    }

    // ---- 仪表盘与健康检查 ----

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

    // ---- 内部工具方法 ----

    private ChatClient buildChatClient(AiModelConfig config) {
        ChatModel chatModel = clientFactory.createChatModel(config);
        return ChatClient.builder(chatModel).build();
    }

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

    private ServerSentEvent<String> buildErrorEvent(String message) {
        return buildDeltaEvent(message);
    }

    private ServerSentEvent<String> buildDoneEvent() {
        return ServerSentEvent.<String>builder()
                .data("{\"type\":\"done\"}")
                .build();
    }

    // ---- 意图拦截 ----

    private boolean isDomainRelated(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        for (String keyword : ALLOWED_KEYWORDS) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ---- 系统提示词 ----

    private String buildSystemPrompt() {
        return """
                你是智能库存调拨系统的 AI Copilot。
                你只能回答与智能库存调拨系统相关的问题。

                允许回答的范围包括：

                1. SKU 库存查询
                2. 仓库库存分析
                3. 缺货风险分析
                4. 库存积压风险分析
                5. 调拨建议解释
                6. 调拨单状态分析
                7. 审批流转说明
                8. 库存流水分析
                9. 补偿任务分析
                10. 系统运营日报
                11. 异常诊断

                禁止回答与本系统无关的问题，包括：

                1. 闲聊
                2. 娱乐
                3. 政治
                4. 通用百科
                5. 与库存调拨无关的编程问题
                6. 写诗、写小说、翻译等通用任务

                如果用户问题与智能库存调拨无关，你必须简短拒绝，并引导用户回到库存、风险、仓库、SKU、调拨单相关问题。

                你不能编造数据库中不存在的数据。
                如果需要实时数据，必须优先调用工具查询。
                如果工具没有返回数据，必须明确说明"未查询到相关数据"。
                你不能直接写 SQL。
                你不能要求用户提供数据库密码。
                你不能生成修改数据库的操作。
                你不能创建、审批、拒绝、锁定、解锁、扣减库存。
                你只能基于后端只读工具查询结果进行分析和解释。

                回答风格：

                1. 使用中文
                2. 结构清晰，使用 Markdown 格式
                3. 库存风险分析优先使用 Markdown 表格
                4. 重要风险直接指出
                5. 涉及数量、仓库、SKU、调拨单时尽量列出具体字段
                6. 不要输出模型内部推理过程
                7. 不要输出工具调用原始 JSON，除非用户明确要求排查技术问题
                """;
    }
}
