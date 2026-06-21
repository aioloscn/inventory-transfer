package com.sits.transfer.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Transfer order event — sent via RocketMQ for async processing.
 */
@Data
public class TransferOrderEventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private String transferNo;
    private Long skuId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer quantity;
    private String status;
    private String operator;
    private LocalDateTime timestamp;

    public TransferOrderEventMessage() {}

    public static TransferOrderEventMessage of(String eventId, String eventType, String transferNo) {
        TransferOrderEventMessage msg = new TransferOrderEventMessage();
        msg.eventId = eventId;
        msg.eventType = eventType;
        msg.transferNo = transferNo;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }
}
