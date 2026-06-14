package com.sits.common.enums;

/**
 * Inventory risk level.
 */
public enum RiskLevel {
    HIGH("高风险"),
    MEDIUM("中风险"),
    LOW("低风险");

    private final String desc;

    RiskLevel(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
