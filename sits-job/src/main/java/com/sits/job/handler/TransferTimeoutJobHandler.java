package com.sits.job.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.common.enums.TransferOrderStatus;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.mapper.TransferOrderMapper;
import com.sits.transfer.service.TransferOrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * XXL-Job handler: Transfer Order Timeout Detection.
 *
 * <p>Detects transfer orders stuck in intermediate states for too long
 * and either auto-cancels (releasing inventory) or creates compensation tasks.
 *
 * <p>Timeout thresholds (configurable):
 * <ul>
 *   <li>APPROVING: 24 hours</li>
 *   <li>OUTBOUNDING: 2 hours</li>
 *   <li>INBOUNDING: 4 hours</li>
 * </ul>
 */
@Component
public class TransferTimeoutJobHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferTimeoutJobHandler.class);

    /** 超时阈值（分钟） */
    private static final int APPROVING_TIMEOUT_MIN = 1440; // 24 小时
    private static final int OUTBOUNDING_TIMEOUT_MIN = 120; // 2 小时
    private static final int INBOUNDING_TIMEOUT_MIN = 240;  // 4 小时

    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderService transferOrderService;

    public TransferTimeoutJobHandler(TransferOrderMapper transferOrderMapper,
                                     TransferOrderService transferOrderService) {
        this.transferOrderMapper = transferOrderMapper;
        this.transferOrderService = transferOrderService;
    }

    @XxlJob("transferTimeoutJob")
    public void execute() {
        log.info(">>>>>>>>>>> XXL-Job: transferTimeoutJob start");

        int total = 0;

        total += handleTimeout(TransferOrderStatus.APPROVING, APPROVING_TIMEOUT_MIN);
        total += handleTimeout(TransferOrderStatus.OUTBOUNDING, OUTBOUNDING_TIMEOUT_MIN);
        total += handleTimeout(TransferOrderStatus.INBOUNDING, INBOUNDING_TIMEOUT_MIN);

        log.info(">>>>>>>>>>> XXL-Job: transferTimeoutJob done, {} timed-out orders handled", total);
    }

    private int handleTimeout(TransferOrderStatus status, int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);

        List<TransferOrder> orders = transferOrderMapper.selectList(
                new LambdaQueryWrapper<TransferOrder>()
                        .eq(TransferOrder::getStatus, status.name())
                        .le(TransferOrder::getUpdateTime, deadline)
        );

        for (TransferOrder order : orders) {
            log.warn("Transfer order {} timeout in status {} (since {}). Auto-cancelling.",
                    order.getTransferNo(), status, order.getUpdateTime());

            try {
                // 通过 service 取消，自动处理库存释放
                transferOrderService.cancel(order.getTransferNo(), "SYSTEM_TIMEOUT");
            } catch (Exception e) {
                log.error("Failed to auto-cancel timed-out order: {}", order.getTransferNo(), e);
            }
        }

        return orders.size();
    }
}
