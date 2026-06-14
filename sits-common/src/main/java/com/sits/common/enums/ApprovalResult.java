package com.sits.common.enums;

/**
 * Approval result.
 */
public enum ApprovalResult {
    APPROVED("通过"),
    REJECTED("拒绝");

    private final String desc;

    ApprovalResult(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
