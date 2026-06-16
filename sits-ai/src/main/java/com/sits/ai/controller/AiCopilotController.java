package com.sits.ai.controller;

import com.sits.ai.service.AiReportService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Copilot API — 聊天、日报生成、异常诊断。
 *
 * <p>AI 可自动调用以下只读函数获取数据：
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
    private final List<FunctionCallback> functionCallbacks;
    private final AiReportService aiReportService;

    public AiCopilotController(ChatClient chatClient,
                               List<FunctionCallback> functionCallbacks,
                               AiReportService aiReportService) {
        this.chatClient = chatClient;
        this.functionCallbacks = functionCallbacks;
        this.aiReportService = aiReportService;
    }

    /**
     * 聊天接口 — 发送自然语言问题，AI 自动调用工具函数获取数据后回答。
     */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");

        String answer = chatClient.prompt()
                .tools(functionCallbacks.toArray(new FunctionCallback[0]))
                .user(question)
                .call()
                .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        result.put("answer", answer != null ? answer : "AI 未生成回答");
        result.put("availableTools", functionCallbacks.stream()
                .map(FunctionCallback::getName)
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 生成运营日报 — 自动收集系统数据，交给 AI 生成日报。
     *
     * <p>日报内容包括：风险统计、调拨单状态分布、调拨建议统计、补偿任务等。
     * <p>AI 会对数据进行解读，指出需要关注的问题，并给出行动建议。
     */
    @PostMapping("/daily-report")
    public Map<String, Object> dailyReport() {
        // 收集系统运行数据
        Map<String, Object> reportData = aiReportService.gatherReportData();
        String context = aiReportService.formatReportContext(reportData);

        // 构造 AI 日报提示词
        String prompt = context + "\n\n"
                + "请根据以上数据，生成一份专业的「库存运营日报」（日期：" + LocalDate.now() + "）。要求：\n"
                + "1. 先概述今日整体运营概况（一句话总结）\n"
                + "2. 风险分析：解读风险分布，重点关注严重和高风险项\n"
                + "3. 调拨进度：分析调拨单各状态占比，指出异常（取消/失败）的调拨单\n"
                + "4. 行动建议：给出 2-3 条具体的运营建议\n"
                + "格式使用 Markdown，层次分明，数据准确。";

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", LocalDate.now().toString());
        result.put("answer", answer != null ? answer : "AI 未生成回答");
        result.put("reportData", reportData);
        return result;
    }

    /**
     * 异常诊断 — 针对指定调拨单，查询其详情和操作日志，交给 AI 诊断问题。
     *
     * <p>请求体: {"transferNo": "TO202601010001"}
     * <p>AI 会分析调拨单的完整生命周期日志，判断是否存在异常（如卡在某状态过久、
     * 取消原因、失败原因等），并给出处理建议。
     */
    @PostMapping("/diagnose")
    public Map<String, Object> diagnose(@RequestBody Map<String, String> request) {
        String transferNo = request.getOrDefault("transferNo", "");

        // 收集诊断数据：调拨单详情 + 操作日志 + 源/目标库存
        Map<String, Object> diagnoseData = aiReportService.gatherDiagnoseData(transferNo);

        if (diagnoseData.containsKey("error")) {
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("transferNo", transferNo);
            errorResult.put("answer", "诊断失败：" + diagnoseData.get("error"));
            return errorResult;
        }

        // 构造诊断提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下调拨单进行异常诊断分析：\n\n");
        prompt.append("【调拨单信息】\n");
        prompt.append(diagnoseData.get("transferOrder")).append("\n\n");
        prompt.append("【操作日志】\n");
        prompt.append(diagnoseData.get("logs")).append("\n\n");
        prompt.append("【源仓库库存】").append(diagnoseData.get("sourceInventory")).append("\n");
        prompt.append("【目标仓库库存】").append(diagnoseData.get("targetInventory")).append("\n\n");
        prompt.append("请分析：\n");
        prompt.append("1. 该调拨单当前处于什么状态，是否正常\n");
        prompt.append("2. 流程是否有异常（如某步骤耗时异常、卡在某个状态过久）\n");
        prompt.append("3. 如果已取消或失败，可能的原因是什么\n");
        prompt.append("4. 给出处理建议\n");
        prompt.append("格式使用 Markdown，简洁明了。");

        String answer = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transferNo", transferNo);
        result.put("answer", answer != null ? answer : "AI 未生成回答");
        result.put("diagnoseData", diagnoseData);
        return result;
    }

    /**
     * 仪表盘统计数据 — 返回前端仪表盘所需的全部统计数值。
     *
     * <p>包含：四大卡片（风险数/调拨单数/待审批数/建议数）、
     * 风险按类型和等级分布、调拨单状态分布、最近风险动态。
     */
    @GetMapping("/dashboard-stats")
    public Map<String, Object> dashboardStats() {
        return aiReportService.gatherDashboardStats();
    }

    /**
     * AI 模块健康检查。
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("module", "ai-copilot");
        status.put("toolsCount", String.valueOf(functionCallbacks.size()));
        return status;
    }
}
