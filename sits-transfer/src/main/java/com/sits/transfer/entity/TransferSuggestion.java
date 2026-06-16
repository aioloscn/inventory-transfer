package com.sits.transfer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transfer suggestion entity.
 */
@TableName("transfer_suggestion")
@Data
public class TransferSuggestion {

    private Long id;
    private String suggestionNo;
    private Long riskId;
    private Long skuId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer suggestQuantity;
    private BigDecimal estimatedCost;
    private BigDecimal score;
    private String reason;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
