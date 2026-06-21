package com.sits.approval.service;

import com.sits.common.entity.ApprovalRecord;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;

import java.util.List;

/**
 * Approval service.
 */
public interface ApprovalService {

    /**
     * Record an approval action.
     */
    ApprovalRecord record(String bizType, String bizNo, Long approverId,
                           String result, String comment);

    /**
     * Query approval records by business number.
     */
    List<ApprovalRecord> listByBizNo(String bizNo);

    /**
     * Paged query for approval records, optionally filtered by approveResult.
     */
    PageResult<ApprovalRecord> page(PageQuery query, String status);

    /**
     * Approve a pending approval record, updating the transfer order accordingly.
     */
    void approve(Long id, String comment);

    /**
     * Reject a pending approval record, updating the transfer order accordingly.
     */
    void reject(Long id, String comment);
}
