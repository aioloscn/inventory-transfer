package com.sits.transfer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Transfer order log — records every status transition.
 */
@TableName("transfer_order_log")
@Data
public class TransferOrderLog {

    private Long id;
    private String transferNo;
    private String fromStatus;
    private String toStatus;
    private String event;
    private String operator;
    private String remark;
    private LocalDateTime createTime;
}
