package com.sits.common.enums;

/**
 * Transfer suggestion status.
 */
public enum SuggestionStatus {
    GENERATED("已生成"),
    CONFIRMED("已确认"),
    REJECTED("已拒绝"),
    EXPIRED("已过期"),
    CONVERTED("已转调拨单");

    private final String desc;

    SuggestionStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
