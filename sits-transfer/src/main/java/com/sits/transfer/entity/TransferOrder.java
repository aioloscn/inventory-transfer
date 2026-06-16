package com.sits.transfer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.sits.common.base.BaseEntity;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨单实体。
 */
@TableName("transfer_order")
@Data
public class TransferOrder extends BaseEntity {

    private String transferNo;
    private Long suggestionId;
    private Long skuId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer transferQuantity;
    private BigDecimal transferAmount;
    private String status;
    private Long applicantId;
    private Long approverId;

    @Version
    private Integer version;
}
