package com.sits.common.enums;

/**
 * MQ event types used across the system.
 */
public enum MqEventType {

    INVENTORY_RISK_DETECTED("库存风险检测完成"),
    TRANSFER_SUGGESTION_GENERATED("调拨建议已生成"),
    TRANSFER_ORDER_CREATED("调拨单已创建"),
    TRANSFER_STOCK_LOCKED("调拨库存已锁定"),
    TRANSFER_APPROVAL_PASSED("调拨审批已通过"),
    TRANSFER_OUTBOUND_SUCCESS("调拨出库成功"),
    TRANSFER_INBOUND_SUCCESS("调拨入库成功"),
    TRANSFER_ORDER_FAILED("调拨单失败"),
    COMPENSATION_TASK_CREATED("补偿任务已创建");

    private final String desc;

    MqEventType(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
