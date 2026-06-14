package com.sits.common.enums;

/**
 * Warehouse status.
 */
public enum WarehouseStatus {
    NORMAL(1, "正常"),
    DISABLED(0, "禁用");

    private final int code;
    private final String desc;

    WarehouseStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
