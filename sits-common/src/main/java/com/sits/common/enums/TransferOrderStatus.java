package com.sits.common.enums;

/**
 * Transfer order status — the full lifecycle state machine states.
 */
public enum TransferOrderStatus {

    CREATED("已创建"),
    STOCK_LOCKED("库存已锁定"),
    APPROVING("审批中"),
    APPROVED("审批通过"),
    REJECTED("审批拒绝"),
    OUTBOUNDING("出库中"),
    OUTBOUNDED("已出库"),
    IN_TRANSIT("运输中"),
    INBOUNDING("入库中"),
    COMPLETED("已完成"),
    CANCELLED("已取消"),
    FAILED("调拨失败");

    private final String desc;

    TransferOrderStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
