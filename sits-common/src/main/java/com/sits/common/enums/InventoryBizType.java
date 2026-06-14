package com.sits.common.enums;

/**
 * Business type for inventory flows — identifies the source business domain.
 */
public enum InventoryBizType {

    PURCHASE("采购"),
    SALES("销售"),
    TRANSFER("调拨"),
    STOCK_TAKE("盘点"),
    COMPENSATION("补偿");

    private final String desc;

    InventoryBizType(String desc) {
        this.desc = desc;
    }

    public String getDesc() { return desc; }
}
