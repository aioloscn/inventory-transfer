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
 * 消费者：调拨单事件。
 *
 * <p>处理调拨生命周期事件：
 * <ul>
 *   <li>日志记录 / 监控</li>
 *   <li>触发风险重扫（库存变更后）</li>
 *   <li>处理失败时抛出异常，由 RocketMQ 自动重试，最终进入 DLQ</li>
 * </ul>
 *
 * <p>幂等：由 mq_consume_record 表 (event_id + consumer_group) 保障。
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

        // 幂等检查
        if (riskService.isEventConsumed(msg.getEventId(), CONSUMER_GROUP)) {
            log.info("MQ event already consumed (idempotent): eventId={}", msg.getEventId());
            return;
        }

        process(msg);
        riskService.recordEventConsumed(msg.getEventId(), CONSUMER_GROUP,
                msg.getTransferNo(), msg.getEventType());
    }

    private void process(TransferOrderEventMessage msg) {
        String eventType = msg.getEventType();
        String transferNo = msg.getTransferNo();

        if (MqEventType.TRANSFER_ORDER_CREATED.name().equals(eventType)) {
            log.info("Transfer order created: transferNo={}, skuId={}, qty={}",
                    transferNo, msg.getSkuId(), msg.getQuantity());
            riskService.logEventNotification(eventType, msg.getSkuId(), msg.getSourceWarehouseId(), msg.getQuantity());

        } else if (MqEventType.TRANSFER_APPROVAL_PASSED.name().equals(eventType)) {
            log.info("Transfer approval passed (stock reserved): transferNo={}, srcWarehouse={}, skuId={}, qty={}",
                    transferNo, msg.getSourceWarehouseId(), msg.getSkuId(), msg.getQuantity());
            // Risk rescan on approval — stock is now locked
            riskService.rescanRisk(msg.getSkuId(), msg.getSourceWarehouseId());
            riskService.logEventNotification(eventType, msg.getSkuId(), msg.getSourceWarehouseId(), msg.getQuantity());

        } else if (MqEventType.TRANSFER_OUTBOUND_SUCCESS.name().equals(eventType)) {
            log.info("Transfer outbound success: transferNo={}, srcWarehouse={}, skuId={}, qty={}",
                    transferNo, msg.getSourceWarehouseId(), msg.getSkuId(), msg.getQuantity());
            riskService.rescanRisk(msg.getSkuId(), msg.getSourceWarehouseId());
            riskService.logEventNotification(eventType, msg.getSkuId(), msg.getSourceWarehouseId(), msg.getQuantity());

        } else if (MqEventType.TRANSFER_INBOUND_SUCCESS.name().equals(eventType)) {
            log.info("Transfer inbound success: transferNo={}, targetWarehouse={}, skuId={}, qty={}",
                    transferNo, msg.getTargetWarehouseId(), msg.getSkuId(), msg.getQuantity());
            riskService.rescanRisk(msg.getSkuId(), msg.getTargetWarehouseId());
            riskService.logEventNotification(eventType, msg.getSkuId(), msg.getTargetWarehouseId(), msg.getQuantity());

        } else if (MqEventType.TRANSFER_ORDER_FAILED.name().equals(eventType)) {
            log.warn("Transfer order failed, will retry then DLQ: transferNo={}", transferNo);
            riskService.logEventNotification(eventType, msg.getSkuId(), msg.getSourceWarehouseId(), msg.getQuantity());
            throw new RuntimeException("Transfer order failed: " + transferNo);

        } else {
            log.debug("Unhandled event type: type={}, transferNo={}", eventType, transferNo);
        }
    }
}
