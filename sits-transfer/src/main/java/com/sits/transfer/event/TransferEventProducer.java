package com.sits.transfer.event;

import com.sits.common.enums.MqEventType;
import com.sits.common.util.NoGenerator;
import com.sits.transfer.entity.TransferOrder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ producer for transfer-order lifecycle events.
 *
 * <p>Topic: transfer-order-event
 * <p>Each event carries a unique eventId for consumer-side idempotency.
 */
@Component
public class TransferEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventProducer.class);

    private static final String TOPIC = "transfer-order-event";

    private final RocketMQTemplate rocketMQTemplate;

    public TransferEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * Send event: Transfer order created.
     */
    public void sendOrderCreated(TransferOrder order) {
        send(order, MqEventType.TRANSFER_ORDER_CREATED);
    }

    /**
     * Send event: Stock locked.
     */
    public void sendStockLocked(TransferOrder order) {
        send(order, MqEventType.TRANSFER_STOCK_LOCKED);
    }

    /**
     * Send event: Approval passed.
     */
    public void sendApprovalPassed(TransferOrder order) {
        send(order, MqEventType.TRANSFER_APPROVAL_PASSED);
    }

    /**
     * Send event: Outbound success.
     */
    public void sendOutboundSuccess(TransferOrder order) {
        send(order, MqEventType.TRANSFER_OUTBOUND_SUCCESS);
    }

    /**
     * Send event: Inbound success.
     */
    public void sendInboundSuccess(TransferOrder order) {
        send(order, MqEventType.TRANSFER_INBOUND_SUCCESS);
    }

    /**
     * Send event: Transfer order failed.
     */
    public void sendOrderFailed(TransferOrder order, String reason) {
        send(order, MqEventType.TRANSFER_ORDER_FAILED);
    }

    // -- private --

    private void send(TransferOrder order, MqEventType eventType) {
        String eventId = NoGenerator.generateEventId();

        TransferOrderEventMessage msg = TransferOrderEventMessage.of(
                eventId, eventType.name(), order.getTransferNo());
        msg.setSkuId(order.getSkuId());
        msg.setSourceWarehouseId(order.getSourceWarehouseId());
        msg.setTargetWarehouseId(order.getTargetWarehouseId());
        msg.setQuantity(order.getTransferQuantity());
        msg.setStatus(order.getStatus());

        try {
            rocketMQTemplate.send(TOPIC,
                    MessageBuilder.withPayload(msg)
                            .setHeader("eventId", eventId)
                            .setHeader("eventType", eventType.name())
                            .build());
            log.info("MQ event sent: topic={}, eventType={}, transferNo={}, eventId={}",
                    TOPIC, eventType, order.getTransferNo(), eventId);
        } catch (Exception e) {
            log.error("Failed to send MQ event: topic={}, eventType={}, transferNo={}",
                    TOPIC, eventType, order.getTransferNo(), e);
            // Don't throw — MQ failure should not block the business operation.
            // Compensation task will be created if needed.
        }
    }
}
