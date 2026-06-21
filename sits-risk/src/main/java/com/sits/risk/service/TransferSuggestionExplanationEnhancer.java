package com.sits.risk.service;

import com.sits.risk.dto.SuggestionExplainContext;

/**
 * 调拨建议解释增强接口。
 *
 * <p>定义在 sits-risk 模块中，避免 sits-risk 直接依赖 sits-ai 造成循环依赖。
 * sits-ai 模块提供 AI 实现，sits-risk 提供默认的直通实现。
 *
 * <p>AI 只能增强 reason 字段，禁止修改调拨数量、仓库等核心字段。
 */
public interface TransferSuggestionExplanationEnhancer {

    /**
     * 增强调拨建议的说明文本。
     *
     * @param context 解释上下文（包含基础reason和所有必要数据）
     * @return 增强后的说明文本；如果增强失败应返回基础reason
     */
    String enhance(SuggestionExplainContext context);
}
