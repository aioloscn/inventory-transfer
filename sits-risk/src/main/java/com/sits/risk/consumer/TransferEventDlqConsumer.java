package com.sits.risk.consumer;

import com.sits.risk.service.RiskService;
import com.sits.transfer.event.TransferOrderEventMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * DLQ 死信队列消费者：调拨单事件。
 *
 * <p>当主消费者 {@link TransferEventConsumer} 重试耗尽后，
 * 消息进入 RocketMQ 默认死信队列（%DLQ%sits-transfer-consumer），
 * 由本消费者创建补偿任务，供 XXL-Job 调度重试。
 *
 * <p>幂等：由 mq_consume_record 表 (event_id + consumer_group) 保障。
 */
@Component
@RocketMQMessageListener(
        topic = "%DLQ%sits-transfer-consumer",
        consumerGroup = "sits-transfer-dlq-consumer",
        selectorExpression = "*"
)
public class TransferEventDlqConsumer implements RocketMQListener<TransferOrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(TransferEventDlqConsumer.class);

    private static final String CONSUMER_GROUP = "sits-transfer-dlq-consumer";

    private final RiskService riskService;

    public TransferEventDlqConsumer(RiskService riskService) {
        this.riskService = riskService;
    }

    @Override
    public void onMessage(TransferOrderEventMessage msg) {
        log.warn("DLQ received: eventType={}, transferNo={}, eventId={}",
                msg.getEventType(), msg.getTransferNo(), msg.getEventId());

        // 幂等检查
        if (riskService.isEventConsumed(msg.getEventId(), CONSUMER_GROUP)) {
            log.info("DLQ event already consumed (idempotent): eventId={}", msg.getEventId());
            return;
        }

        riskService.createCompensationTask("TRANSFER", msg.getTransferNo(),
                "MQ processing exhausted retries, eventType=" + msg.getEventType());
        riskService.recordEventConsumed(msg.getEventId(), CONSUMER_GROUP,
                msg.getTransferNo(), msg.getEventType());

        log.info("DLQ compensation task created: transferNo={}, eventType={}",
                msg.getTransferNo(), msg.getEventType());
    }
}
