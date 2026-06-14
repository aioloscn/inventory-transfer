package com.sits.approval.controller;

import com.sits.approval.entity.ApprovalRecord;
import com.sits.approval.service.ApprovalService;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Approval record API.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * List approval records for a given business number (transferNo).
     */
    @GetMapping("/by-biz/{bizNo}")
    public Result<List<ApprovalRecord>> listByBizNo(@PathVariable String bizNo) {
        return Result.success(approvalService.listByBizNo(bizNo));
    }

    /**
     * Record an approval (called internally by transfer service).
     */
    @PostMapping
    public Result<ApprovalRecord> record(
            @RequestParam String bizType,
            @RequestParam String bizNo,
            @RequestParam Long approverId,
            @RequestParam String result,
            @RequestParam(required = false) String comment) {
        return Result.success(approvalService.record(bizType, bizNo, approverId, result, comment));
    }
}
