package com.sits.common.enums;

/**
 * SKU status.
 */
public enum SkuStatus {
    NORMAL(1, "正常"),
    DISABLED(0, "禁用");

    private final int code;
    private final String desc;

    SkuStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
