package com.sits.common.enums;

/**
 * 库存预占状态。
 */
public enum ReservationStatus {

    /** 已预占 — 审批通过后库存被锁定 */
    RESERVED("已预占"),

    /** 已核销 — 出库时预占被消耗（实际库存扣减） */
    WRITTEN_OFF("已核销"),

    /** 已释放 — 驳回/撤销/超时时预占归还 */
    RELEASED("已释放");

    private final String desc;

    ReservationStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
