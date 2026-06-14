package com.sits.common.enums;

/**
 * Inventory risk type.
 */
public enum RiskType {
    SHORTAGE("缺货风险"),
    OVERSTOCK("积压风险");

    private final String desc;

    RiskType(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
