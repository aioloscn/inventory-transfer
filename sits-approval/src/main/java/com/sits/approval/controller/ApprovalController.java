package com.sits.approval.controller;

import com.sits.common.entity.ApprovalRecord;
import com.sits.approval.service.ApprovalService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * Paged query for approval records.
     */
    @GetMapping("/page")
    public Result<PageResult<ApprovalRecord>> page(PageQuery query,
                                                    @RequestParam(required = false) String status) {
        return Result.success(approvalService.page(query, status));
    }

    /**
     * Approve a pending approval.
     */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        approvalService.approve(id, body.get("comment"));
        return Result.success();
    }

    /**
     * Reject a pending approval.
     */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        approvalService.reject(id, body.get("comment"));
        return Result.success();
    }
}
