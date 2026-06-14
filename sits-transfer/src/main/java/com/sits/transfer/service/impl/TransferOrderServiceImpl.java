package com.sits.transfer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.enums.TransferOrderEvent;
import com.sits.common.enums.TransferOrderStatus;
import com.sits.common.exception.BusinessException;
import com.sits.common.exception.StateTransitionException;
import com.sits.common.util.NoGenerator;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.service.InventoryService;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferOrderLog;
import com.sits.transfer.mapper.TransferOrderLogMapper;
import com.sits.transfer.mapper.TransferOrderMapper;
import com.sits.transfer.service.TransferOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * TransferOrderService implementation.
 *
 * <p>Uses Spring StateMachine to drive status transitions.
 * Each transition:
 * <ol>
 *   <li>Sends the event to the state machine — validates the transition is legal.</li>
 *   <li>Updates the transfer_order.status in DB.</li>
 *   <li>Records a transfer_order_log entry.</li>
 *   <li>Performs inventory operations (lock/unlock/deduct/inbound).</li>
 * </ol>
 */
@Service
public class TransferOrderServiceImpl implements TransferOrderService {

    private static final Logger log = LoggerFactory.getLogger(TransferOrderServiceImpl.class);

    private final TransferOrderMapper orderMapper;
    private final TransferOrderLogMapper logMapper;
    private final InventoryService inventoryService;
    private final StateMachineFactory<TransferOrderStatus, TransferOrderEvent> stateMachineFactory;

    public TransferOrderServiceImpl(TransferOrderMapper orderMapper,
                                     TransferOrderLogMapper logMapper,
                                     InventoryService inventoryService,
                                     StateMachineFactory<TransferOrderStatus, TransferOrderEvent> stateMachineFactory) {
        this.orderMapper = orderMapper;
        this.logMapper = logMapper;
        this.inventoryService = inventoryService;
        this.stateMachineFactory = stateMachineFactory;
    }

    // ==================== CREATE ====================

    @Override
    @Transactional
    public TransferOrder create(Long skuId, Long sourceWarehouseId, Long targetWarehouseId,
                                 int quantity, Long applicantId) {
        if (quantity <= 0) {
            throw new BusinessException("调拨数量必须大于0");
        }
        if (sourceWarehouseId.equals(targetWarehouseId)) {
            throw new BusinessException("调出仓和调入仓不能相同");
        }

        TransferOrder order = new TransferOrder();
        order.setTransferNo(NoGenerator.generateTransferNo());
        order.setSkuId(skuId);
        order.setSourceWarehouseId(sourceWarehouseId);
        order.setTargetWarehouseId(targetWarehouseId);
        order.setTransferQuantity(quantity);
        order.setStatus(TransferOrderStatus.CREATED.name());
        order.setApplicantId(applicantId);

        orderMapper.insert(order);

        // Record log
        recordLog(order, null, TransferOrderStatus.CREATED, "CREATE", null);

        log.info("Transfer order created: {}", order.getTransferNo());
        return order;
    }

    // ==================== LOCK STOCK ====================

    @Override
    @Transactional
    public void lockStock(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.LOCK_STOCK);

        // Lock inventory at source warehouse
        try {
            inventoryService.lockStock(order.getSkuId(), order.getSourceWarehouseId(),
                    order.getTransferQuantity(), order.getTransferNo(), operator);
        } catch (Exception e) {
            log.error("Stock lock failed for {}", transferNo, e);
            throw new BusinessException("库存锁定失败: " + e.getMessage());
        }

        order.setStatus(TransferOrderStatus.STOCK_LOCKED.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.CREATED, TransferOrderStatus.STOCK_LOCKED,
                "LOCK_STOCK", operator);

        log.info("Stock locked for transfer: {}", transferNo);
    }

    // ==================== SUBMIT APPROVAL ====================

    @Override
    @Transactional
    public void submitApproval(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.SUBMIT_APPROVAL);

        order.setStatus(TransferOrderStatus.APPROVING.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.STOCK_LOCKED, TransferOrderStatus.APPROVING,
                "SUBMIT_APPROVAL", operator);
    }

    // ==================== APPROVE ====================

    @Override
    @Transactional
    public void approve(String transferNo, String approver, String comment) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.APPROVE);

        order.setStatus(TransferOrderStatus.APPROVED.name());
        order.setApproverId(parseApproverId(approver));
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.APPROVING, TransferOrderStatus.APPROVED,
                "APPROVE", approver);

        log.info("Transfer approved: {}", transferNo);
    }

    // ==================== REJECT ====================

    @Override
    @Transactional
    public void reject(String transferNo, String approver, String comment) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.REJECT);

        // Release locked stock
        inventoryService.unlockStock(order.getSkuId(), order.getSourceWarehouseId(),
                order.getTransferQuantity(), order.getTransferNo(), approver);

        order.setStatus(TransferOrderStatus.REJECTED.name());
        order.setApproverId(parseApproverId(approver));
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.APPROVING, TransferOrderStatus.REJECTED,
                "REJECT", approver);

        log.info("Transfer rejected: {}", transferNo);
    }

    // ==================== START OUTBOUND ====================

    @Override
    @Transactional
    public void startOutbound(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.START_OUTBOUND);

        order.setStatus(TransferOrderStatus.OUTBOUNDING.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.APPROVED, TransferOrderStatus.OUTBOUNDING,
                "START_OUTBOUND", operator);
    }

    // ==================== OUTBOUND SUCCESS ====================

    @Override
    @Transactional
    public void outboundSuccess(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.OUTBOUND_SUCCESS);

        // Deduct locked stock -> in-transit at source warehouse
        inventoryService.outboundStock(order.getSkuId(), order.getSourceWarehouseId(),
                order.getTransferQuantity(), order.getTransferNo(), operator);

        order.setStatus(TransferOrderStatus.OUTBOUNDED.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.OUTBOUNDING, TransferOrderStatus.OUTBOUNDED,
                "OUTBOUND_SUCCESS", operator);
    }

    // ==================== START SHIP ====================

    @Override
    @Transactional
    public void startShip(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.START_SHIP);

        order.setStatus(TransferOrderStatus.IN_TRANSIT.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.OUTBOUNDED, TransferOrderStatus.IN_TRANSIT,
                "START_SHIP", operator);
    }

    // ==================== START INBOUND ====================

    @Override
    @Transactional
    public void startInbound(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.START_INBOUND);

        order.setStatus(TransferOrderStatus.INBOUNDING.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.IN_TRANSIT, TransferOrderStatus.INBOUNDING,
                "START_INBOUND", operator);
    }

    // ==================== INBOUND SUCCESS ====================

    @Override
    @Transactional
    public void inboundSuccess(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.INBOUND_SUCCESS);

        // Inbound stock at target warehouse
        inventoryService.inboundStock(order.getSkuId(), order.getTargetWarehouseId(),
                order.getTransferQuantity(), order.getTransferNo(), operator);

        // Release in-transit at source warehouse
        inventoryService.releaseInTransitStock(order.getSkuId(), order.getSourceWarehouseId(),
                order.getTransferQuantity(), order.getTransferNo(), operator);

        order.setStatus(TransferOrderStatus.COMPLETED.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.INBOUNDING, TransferOrderStatus.COMPLETED,
                "INBOUND_SUCCESS", operator);

        log.info("Transfer completed: {}", transferNo);
    }

    // ==================== CANCEL ====================

    @Override
    @Transactional
    public void cancel(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.CANCEL);

        // Release stock if currently locked
        if (TransferOrderStatus.STOCK_LOCKED.name().equals(order.getStatus())) {
            inventoryService.unlockStock(order.getSkuId(), order.getSourceWarehouseId(),
                    order.getTransferQuantity(), order.getTransferNo(), operator);
        }

        TransferOrderStatus prevStatus = TransferOrderStatus.valueOf(order.getStatus());
        order.setStatus(TransferOrderStatus.CANCELLED.name());
        orderMapper.updateById(order);
        recordLog(order, prevStatus, TransferOrderStatus.CANCELLED, "CANCEL", operator);
    }

    // ==================== FAIL ====================

    @Override
    @Transactional
    public void fail(String transferNo, String operator, String remark) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.FAIL);

        TransferOrderStatus prevStatus = TransferOrderStatus.valueOf(order.getStatus());
        order.setStatus(TransferOrderStatus.FAILED.name());
        orderMapper.updateById(order);
        recordLog(order, prevStatus, TransferOrderStatus.FAILED, "FAIL", operator);
    }

    // ==================== QUERIES ====================

    @Override
    public TransferOrder getByTransferNo(String transferNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<TransferOrder>().eq(TransferOrder::getTransferNo, transferNo)
        );
    }

    @Override
    public TransferOrder getById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public PageResult<TransferOrder> page(PageQuery query) {
        var page = orderMapper.selectPage(
                query.toPage(),
                new LambdaQueryWrapper<TransferOrder>().orderByDesc(TransferOrder::getUpdateTime)
        );
        return PageResult.of(page);
    }

    @Override
    public List<TransferOrderLog> listLogs(String transferNo) {
        return logMapper.selectList(
                new LambdaQueryWrapper<TransferOrderLog>()
                        .eq(TransferOrderLog::getTransferNo, transferNo)
                        .orderByAsc(TransferOrderLog::getCreateTime)
        );
    }

    // ==================== PRIVATE HELPERS ====================

    private TransferOrder getOrThrow(String transferNo) {
        TransferOrder order = getByTransferNo(transferNo);
        if (order == null) {
            throw new BusinessException("调拨单不存在: " + transferNo);
        }
        return order;
    }

    /**
     * Validate state transition using the StateMachine.
     */
    private void fireEvent(TransferOrder order, TransferOrderEvent event) {
        StateMachine<TransferOrderStatus, TransferOrderEvent> sm = stateMachineFactory.getStateMachine();
        sm.start();

        // Restore current state
        TransferOrderStatus currentStatus = TransferOrderStatus.valueOf(order.getStatus());
        sm.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachine(
                        new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                                currentStatus, null, null, null)));

        Message<TransferOrderEvent> msg = MessageBuilder
                .withPayload(event)
                .setHeader("transferNo", order.getTransferNo())
                .build();

        boolean accepted = sm.sendEvent(msg);
        if (!accepted) {
            throw new StateTransitionException(order.getStatus(), event.name());
        }
    }

    private void recordLog(TransferOrder order, TransferOrderStatus from,
                           TransferOrderStatus to, String event, String operator) {
        TransferOrderLog logEntry = new TransferOrderLog();
        logEntry.setTransferNo(order.getTransferNo());
        logEntry.setFromStatus(from != null ? from.name() : null);
        logEntry.setToStatus(to.name());
        logEntry.setEvent(event);
        logEntry.setOperator(operator);
        logMapper.insert(logEntry);
    }

    private Long parseApproverId(String approver) {
        if (approver == null) return null;
        try {
            return Long.parseLong(approver);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
