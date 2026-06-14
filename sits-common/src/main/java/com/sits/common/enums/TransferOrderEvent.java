package com.sits.common.enums;

/**
 * Transfer order events — triggers for state machine transitions.
 */
public enum TransferOrderEvent {

    LOCK_STOCK("锁定库存"),
    SUBMIT_APPROVAL("提交审批"),
    APPROVE("审批通过"),
    REJECT("审批拒绝"),
    START_OUTBOUND("开始出库"),
    OUTBOUND_SUCCESS("出库成功"),
    START_SHIP("开始运输"),
    START_INBOUND("开始入库"),
    INBOUND_SUCCESS("入库成功"),
    CANCEL("取消"),
    FAIL("失败");

    private final String desc;

    TransferOrderEvent(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
