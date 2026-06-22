package com.sits.risk.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 调拨建议生成请求参数。
 */
@Getter
@Setter
public class GenerateSuggestionsRequest {

    /** 可选：只针对指定风险ID生成建议 */
    private List<Long> riskIds;

    /** 可选：本次最多生成多少条，默认100 */
    private Integer maxCount;

    /** 可选：是否启用 AI 原因增强，默认true。仅在 YAML 总开关 enabled 时生效 */
    private Boolean enableAiExplanation;

    /** 可选：是否只预览不落库，默认false */
    private Boolean dryRun;

    // -- default helpers --

    public int getMaxCountOrDefault() {
        return maxCount != null && maxCount > 0 ? maxCount : 100;
    }

    /** 未传值时默认启用 AI（用户需在页面主动关闭才不启用） */
    public boolean isAiEnabled() {
        return enableAiExplanation == null || enableAiExplanation;
    }

    public boolean isDryRun() {
        return dryRun != null && dryRun;
    }
}
