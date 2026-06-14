package com.sits.transfer.service;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferOrderLog;

import java.util.List;

/**
 * Transfer order service — full lifecycle management.
 */
public interface TransferOrderService {

    /**
     * Create a transfer order (status: CREATED).
     */
    TransferOrder create(Long skuId, Long sourceWarehouseId, Long targetWarehouseId,
                         int quantity, Long applicantId);

    /**
     * Lock stock at source warehouse (CREATED -> STOCK_LOCKED).
     */
    void lockStock(String transferNo, String operator);

    /**
     * Submit for approval (STOCK_LOCKED -> APPROVING).
     */
    void submitApproval(String transferNo, String operator);

    /**
     * Approve the transfer (APPROVING -> APPROVED).
     */
    void approve(String transferNo, String approver, String comment);

    /**
     * Reject the transfer (APPROVING -> REJECTED). Stock is released.
     */
    void reject(String transferNo, String approver, String comment);

    /**
     * Start outbound (APPROVED -> OUTBOUNDING).
     */
    void startOutbound(String transferNo, String operator);

    /**
     * Confirm outbound success (OUTBOUNDING -> OUTBOUNDED).
     */
    void outboundSuccess(String transferNo, String operator);

    /**
     * Start shipping (OUTBOUNDED -> IN_TRANSIT).
     */
    void startShip(String transferNo, String operator);

    /**
     * Start inbound at target warehouse (IN_TRANSIT -> INBOUNDING).
     */
    void startInbound(String transferNo, String operator);

    /**
     * Confirm inbound success (INBOUNDING -> COMPLETED).
     */
    void inboundSuccess(String transferNo, String operator);

    /**
     * Cancel transfer order (CREATED / STOCK_LOCKED -> CANCELLED).
     */
    void cancel(String transferNo, String operator);

    /**
     * Mark as failed (OUTBOUNDING / INBOUNDING -> FAILED).
     */
    void fail(String transferNo, String operator, String remark);

    // ---- Queries ----

    TransferOrder getByTransferNo(String transferNo);

    TransferOrder getById(Long id);

    PageResult<TransferOrder> page(PageQuery query);

    List<TransferOrderLog> listLogs(String transferNo);
}
