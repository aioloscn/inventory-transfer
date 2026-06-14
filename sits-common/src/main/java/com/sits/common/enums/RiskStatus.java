package com.sits.common.enums;

/**
 * Risk record status.
 */
public enum RiskStatus {
    NEW("新建"),
    PROCESSING("处理中"),
    RESOLVED("已解决"),
    IGNORED("已忽略");

    private final String desc;

    RiskStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
