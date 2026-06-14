package com.sits.common.enums;

/**
 * Compensation task status.
 */
public enum CompensationStatus {
    PENDING("待处理"),
    RETRYING("重试中"),
    SUCCESS("已成功"),
    FAILED("已失败"),
    MANUAL("需人工处理");

    private final String desc;

    CompensationStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
