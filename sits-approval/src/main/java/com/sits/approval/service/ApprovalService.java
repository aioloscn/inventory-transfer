package com.sits.approval.service;

import com.sits.approval.entity.ApprovalRecord;

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
}
