package com.sits.common.enums;

/**
 * Inventory change type — records the specific operation that caused the inventory change.
 */
public enum InventoryChangeType {

    PURCHASE_IN("采购入库", "IN"),
    SALES_OUT("销售出库", "OUT"),
    TRANSFER_LOCK("调拨锁定", "LOCK"),
    TRANSFER_UNLOCK("调拨释放", "UNLOCK"),
    TRANSFER_OUTBOUND("调拨出库", "OUT"),
    TRANSFER_INBOUND("调拨入库", "IN"),
    STOCK_ADJUST("库存盘点调整", "ADJUST"),
    COMPENSATION("补偿调整", "COMPENSATION");

    private final String desc;
    private final String direction; // IN, OUT, LOCK, UNLOCK, ADJUST

    InventoryChangeType(String desc, String direction) {
        this.desc = desc;
        this.direction = direction;
    }

    public String getDesc() { return desc; }
    public String getDirection() { return direction; }
}
