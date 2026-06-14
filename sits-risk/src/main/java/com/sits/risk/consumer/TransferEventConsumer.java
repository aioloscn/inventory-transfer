package com.sits.risk.consumer;

import com.sits.common.enums.MqEventType;
import com.sits.risk.service.RiskService;
import com.sits.transfer.event.TransferOrderEventMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumer: Transfer order events.
 *
 * <p>Handles transfer lifecycle events for:
 * <ul>
 *   <li>Logging / monitoring</li>
 *   <li>Triggering risk re-scan (after inventory changes)</li>
 *   <li>Creating compensation tasks on failure</li>
 * </ul>
 *
 * <p>Idempotency: guarded by mq_consume_record (event_id + consumer_group).
 */
@Component
@RocketMQMessageListener(
        topic = "transfer-order-event",
        consumerGroup = "sits-transfer-consumer",
        selectorExpression = "*"
)
public class TransferEventConsumer implements RocketMQListener<TransferOrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private static final String CONSUMER_GROUP = "sits-transfer-consumer";

    private final RiskService riskService;

    public TransferEventConsumer(RiskService riskService) {
        this.riskService = riskService;
    }

    @Override
    public void onMessage(TransferOrderEventMessage msg) {
        log.info("MQ received: eventType={}, transferNo={}, eventId={}",
                msg.getEventType(), msg.getTransferNo(), msg.getEventId());

        // Idempotency guard
        if (riskService.isEventConsumed(msg.getEventId(), CONSUMER_GROUP)) {
            log.info("MQ event already consumed (idempotent): eventId={}", msg.getEventId());
            return;
        }

        try {
            process(msg);
            riskService.recordEventConsumed(msg.getEventId(), CONSUMER_GROUP,
                    msg.getTransferNo(), msg.getEventType());
        } catch (Exception e) {
            log.error("Failed to process MQ event: eventId={}, transferNo={}",
                    msg.getEventId(), msg.getTransferNo(), e);

            // Create compensation task for failed events
            if (MqEventType.TRANSFER_ORDER_FAILED.name().equals(msg.getEventType())) {
                riskService.createCompensationTask("TRANSFER", msg.getTransferNo(),
                        "MQ processing failed: " + e.getMessage());
            }
        }
    }

    private void process(TransferOrderEventMessage msg) {
        String eventType = msg.getEventType();
        log.info("Processing transfer event: type={}, transferNo={}", eventType, msg.getTransferNo());

        // Currently logging only — future: trigger risk re-scan, notify downstream, etc.
        if (MqEventType.TRANSFER_ORDER_FAILED.name().equals(eventType)) {
            log.warn("Transfer order {} failed, consider creating compensation task", msg.getTransferNo());
            riskService.createCompensationTask("TRANSFER", msg.getTransferNo(),
                    "Transfer order failed, event received via MQ");
        }
    }
}
