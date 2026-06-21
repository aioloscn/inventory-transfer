package com.sits.transfer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sits.common.mapper.ApprovalRecordMapper;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.entity.ApprovalRecord;
import com.sits.common.enums.ReservationStatus;
import com.sits.common.enums.TransferOrderEvent;
import com.sits.common.enums.TransferOrderStatus;
import com.sits.common.exception.BusinessException;
import com.sits.common.exception.StateTransitionException;
import com.sits.common.exception.StockInsufficientException;
import com.sits.common.util.NoGenerator;
import com.sits.inventory.entity.InventoryReservation;
import com.sits.inventory.entity.WarehouseInventory;
import com.sits.inventory.mapper.InventoryReservationMapper;
import com.sits.inventory.service.InventoryService;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferOrderLog;
import com.sits.transfer.event.TransferEventProducer;
import com.sits.transfer.mapper.TransferOrderLogMapper;
import com.sits.transfer.mapper.TransferOrderMapper;
import com.sits.transfer.service.TransferOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TransferOrderService implementation.
 *
 * <p>Uses Spring StateMachine to drive status transitions.
 * <p>Stock is locked on approval (not before), avoiding long-term stock occupation during review.
 *
 * <p>Core formula: available_stock = actual_stock - reserved_stock
 *
 * <p>Each transition:
 * <ol>
 *   <li>Sends the event to the state machine — validates the transition is legal.</li>
 *   <li>Updates the transfer_order.status in DB.</li>
 *   <li>Records a transfer_order_log entry.</li>
 *   <li>Performs inventory operations (validate/lock/release/deduct/inbound).</li>
 * </ol>
 */
@Service
public class TransferOrderServiceImpl implements TransferOrderService {

    private static final Logger log = LoggerFactory.getLogger(TransferOrderServiceImpl.class);

    private final TransferOrderMapper orderMapper;
    private final TransferOrderLogMapper logMapper;
    private final InventoryService inventoryService;
    private final InventoryReservationMapper reservationMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final StateMachineFactory<TransferOrderStatus, TransferOrderEvent> stateMachineFactory;
    private final TransferEventProducer transferEventProducer;

    public TransferOrderServiceImpl(TransferOrderMapper orderMapper,
                                     TransferOrderLogMapper logMapper,
                                     InventoryService inventoryService,
                                     InventoryReservationMapper reservationMapper,
                                     ApprovalRecordMapper approvalRecordMapper,
                                     StateMachineFactory<TransferOrderStatus, TransferOrderEvent> stateMachineFactory,
                                     TransferEventProducer transferEventProducer) {
        this.orderMapper = orderMapper;
        this.logMapper = logMapper;
        this.inventoryService = inventoryService;
        this.reservationMapper = reservationMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.stateMachineFactory = stateMachineFactory;
        this.transferEventProducer = transferEventProducer;
    }

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

        // MQ event
        transferEventProducer.sendOrderCreated(order);

        return order;
    }

    // ---------------------------------------------------------------
    // Submit for approval (CREATED -> APPROVING)
    // Only validates stock — does NOT lock.
    // Creates an ApprovalRecord so the approval center can process it.
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void submitApproval(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.SUBMIT_APPROVAL);

        // Validate stock availability (no lock)
        validateStockAvailability(order);

        order.setStatus(TransferOrderStatus.APPROVING.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.CREATED, TransferOrderStatus.APPROVING,
                "SUBMIT_APPROVAL", operator);

        // Create approval record (approverId will be set by the actual approver)
        createApprovalRecord(order);

        log.info("Transfer submitted for approval: {}", transferNo);
    }

    // ---------------------------------------------------------------
    // Approve (APPROVING -> APPROVED)
    // Conditional lock stock + create reservation record — all transactional
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void approve(String transferNo, String approver, String comment) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.APPROVE);

        // 1) Conditionally lock stock (available -> locked, with available >= qty check)
        try {
            inventoryService.lockStock(order.getSkuId(), order.getSourceWarehouseId(),
                    order.getTransferQuantity(), order.getTransferNo(), approver);
        } catch (Exception e) {
            log.error("Stock lock failed during approval for {}", transferNo, e);
            throw new BusinessException("库存预占失败: " + e.getMessage());
        }

        // 2) Create reservation record (idempotent via unique key on transfer_order_id, sku_id, warehouse_id)
        createReservation(order);

        // 3) Update order status
        order.setStatus(TransferOrderStatus.APPROVED.name());
        order.setApproverId(parseApproverId(approver));
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.APPROVING, TransferOrderStatus.APPROVED,
                "APPROVE", approver);

        log.info("Transfer approved, stock reserved: {}", transferNo);

        // MQ event
        transferEventProducer.sendApprovalPassed(order);
    }

    // ---------------------------------------------------------------
    // Reject (APPROVING -> REJECTED)
    // No stock to release — stock was never locked
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void reject(String transferNo, String approver, String comment) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.REJECT);

        order.setStatus(TransferOrderStatus.REJECTED.name());
        order.setApproverId(parseApproverId(approver));
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.APPROVING, TransferOrderStatus.REJECTED,
                "REJECT", approver);

        log.info("Transfer rejected: {}", transferNo);
    }

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

    // ---------------------------------------------------------------
    // Outbound success (OUTBOUNDING -> OUTBOUNDED)
    // Deduct actual stock (locked -> in_transit) + write off reservation
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void outboundSuccess(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.OUTBOUND_SUCCESS);

        // Deduct locked stock -> in-transit at source warehouse
        inventoryService.outboundStock(order.getSkuId(), order.getSourceWarehouseId(),
                order.getTransferQuantity(), order.getTransferNo(), operator);

        // Write off reservation
        writeOffReservation(order.getId());

        order.setStatus(TransferOrderStatus.OUTBOUNDED.name());
        orderMapper.updateById(order);
        recordLog(order, TransferOrderStatus.OUTBOUNDING, TransferOrderStatus.OUTBOUNDED,
                "OUTBOUND_SUCCESS", operator);

        // MQ event
        transferEventProducer.sendOutboundSuccess(order);
    }

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

        // MQ event
        transferEventProducer.sendInboundSuccess(order);
    }

    // ---------------------------------------------------------------
    // Cancel (CREATED / APPROVING / APPROVED -> CANCELLED)
    // If APPROVED (stock was locked), release reserved stock
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public void cancel(String transferNo, String operator) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.CANCEL);

        TransferOrderStatus prevStatus = TransferOrderStatus.valueOf(order.getStatus());

        // Release reserved stock if approved (stock was locked on approval)
        if (TransferOrderStatus.APPROVED.equals(prevStatus)) {
            inventoryService.unlockStock(order.getSkuId(), order.getSourceWarehouseId(),
                    order.getTransferQuantity(), order.getTransferNo(), operator);
            releaseReservation(order.getId());
        }

        order.setStatus(TransferOrderStatus.CANCELLED.name());
        orderMapper.updateById(order);
        recordLog(order, prevStatus, TransferOrderStatus.CANCELLED, "CANCEL", operator);

        log.info("Transfer cancelled: {}", transferNo);
    }

    @Override
    @Transactional
    public void fail(String transferNo, String operator, String remark) {
        TransferOrder order = getOrThrow(transferNo);
        fireEvent(order, TransferOrderEvent.FAIL);

        TransferOrderStatus prevStatus = TransferOrderStatus.valueOf(order.getStatus());
        order.setStatus(TransferOrderStatus.FAILED.name());
        orderMapper.updateById(order);
        recordLog(order, prevStatus, TransferOrderStatus.FAILED, "FAIL", operator);

        // MQ event
        transferEventProducer.sendOrderFailed(order, remark);
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

    // ==================== Private Helpers ====================

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
        sm.startReactively().block();

        // Restore current state
        TransferOrderStatus currentStatus = TransferOrderStatus.valueOf(order.getStatus());
        sm.getStateMachineAccessor()
                .doWithAllRegions(access -> access.resetStateMachineReactively(
                        new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                                currentStatus, null, null, null)).block());

        Message<TransferOrderEvent> msg = MessageBuilder
                .withPayload(event)
                .setHeader("transferNo", order.getTransferNo())
                .build();

        boolean accepted = Boolean.TRUE.equals(sm.sendEvent(reactor.core.publisher.Mono.just(msg))
                .any(r -> r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED)
                .block());
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

    /**
     * Validate stock availability for the transfer order.
     * No lock — only checks if available_stock >= transfer_qty.
     */
    private void validateStockAvailability(TransferOrder order) {
        WarehouseInventory inv = inventoryService.getBySkuAndWarehouse(
                order.getSkuId(), order.getSourceWarehouseId());
        if (inv == null || inv.getAvailableStock() == null || inv.getAvailableStock() < order.getTransferQuantity()) {
            int available = inv != null && inv.getAvailableStock() != null ? inv.getAvailableStock() : 0;
            throw new StockInsufficientException(order.getSkuId(), order.getSourceWarehouseId(),
                    order.getTransferQuantity(), available);
        }
        log.info("Stock validation passed: skuId={}, warehouseId={}, available={}, need={}",
                order.getSkuId(), order.getSourceWarehouseId(), inv.getAvailableStock(), order.getTransferQuantity());
    }

    /**
     * Create reservation record (idempotent via unique key).
     */
    private void createReservation(TransferOrder order) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationNo(NoGenerator.generateReservationNo());
        reservation.setTransferOrderId(order.getId());
        reservation.setSkuId(order.getSkuId());
        reservation.setWarehouseId(order.getSourceWarehouseId());
        reservation.setQuantity(order.getTransferQuantity());
        reservation.setStatus(ReservationStatus.RESERVED.name());

        try {
            reservationMapper.insert(reservation);
            log.info("Reservation created: reservationNo={}, transferOrderId={}",
                    reservation.getReservationNo(), order.getId());
        } catch (Exception e) {
            log.warn("Reservation duplicate (idempotent): transferOrderId={}", order.getId());
            throw new BusinessException("预占记录重复，操作幂等跳过: " + order.getTransferNo());
        }
    }

    /**
     * Write off reservation — called on outbound success.
     */
    private void writeOffReservation(Long transferOrderId) {
        LambdaUpdateWrapper<InventoryReservation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InventoryReservation::getTransferOrderId, transferOrderId)
                .eq(InventoryReservation::getStatus, ReservationStatus.RESERVED.name())
                .set(InventoryReservation::getStatus, ReservationStatus.WRITTEN_OFF.name());

        int rows = reservationMapper.update(null, wrapper);
        log.info("Reservation written off: transferOrderId={}, rows={}", transferOrderId, rows);
    }

    /**
     * Release reservation — called on cancel/reject/timeout.
     */
    private void releaseReservation(Long transferOrderId) {
        LambdaUpdateWrapper<InventoryReservation> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(InventoryReservation::getTransferOrderId, transferOrderId)
                .eq(InventoryReservation::getStatus, ReservationStatus.RESERVED.name())
                .set(InventoryReservation::getStatus, ReservationStatus.RELEASED.name());

        int rows = reservationMapper.update(null, wrapper);
        log.info("Reservation released: transferOrderId={}, rows={}", transferOrderId, rows);
    }

    /**
     * Create approval record — PENDING, no approver assigned yet.
     * The actual approver will be filled in when approve/reject is called.
     */
    private void createApprovalRecord(TransferOrder order) {
        ApprovalRecord record = new ApprovalRecord();
        record.setBizType("TRANSFER");
        record.setBizNo(order.getTransferNo());
        record.setApproveResult("PENDING");
        approvalRecordMapper.insert(record);
        log.info("Approval record created: bizNo={}", order.getTransferNo());
    }
}
