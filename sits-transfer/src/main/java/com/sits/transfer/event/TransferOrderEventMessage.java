package com.sits.transfer.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Transfer order event — sent via RocketMQ for async processing.
 */
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

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long sourceWarehouseId) { this.sourceWarehouseId = sourceWarehouseId; }
    public Long getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(Long targetWarehouseId) { this.targetWarehouseId = targetWarehouseId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
