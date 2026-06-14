package com.sits.transfer.controller;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.base.Result;
import com.sits.transfer.entity.TransferOrder;
import com.sits.transfer.entity.TransferOrderLog;
import com.sits.transfer.service.TransferOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Transfer order API — full lifecycle management.
 */
@RestController
@RequestMapping("/api/transfer-orders")
public class TransferOrderController {

    private final TransferOrderService transferOrderService;

    public TransferOrderController(TransferOrderService transferOrderService) {
        this.transferOrderService = transferOrderService;
    }

    @PostMapping
    public Result<TransferOrder> create(
            @RequestParam Long skuId,
            @RequestParam Long sourceWarehouseId,
            @RequestParam Long targetWarehouseId,
            @RequestParam int quantity,
            @RequestParam(required = false) Long applicantId) {
        return Result.success(transferOrderService.create(skuId, sourceWarehouseId, targetWarehouseId, quantity, applicantId));
    }

    @GetMapping
    public Result<PageResult<TransferOrder>> page(PageQuery query) {
        return Result.success(transferOrderService.page(query));
    }

    @GetMapping("/{transferNo}")
    public Result<TransferOrder> getByTransferNo(@PathVariable String transferNo) {
        return Result.success(transferOrderService.getByTransferNo(transferNo));
    }

    @GetMapping("/{transferNo}/logs")
    public Result<List<TransferOrderLog>> listLogs(@PathVariable String transferNo) {
        return Result.success(transferOrderService.listLogs(transferNo));
    }

    // -- State transitions --

    @PostMapping("/{transferNo}/lock-stock")
    public Result<Void> lockStock(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.lockStock(transferNo, operator);
        return Result.success();
    }

    @PostMapping("/{transferNo}/submit-approval")
    public Result<Void> submitApproval(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.submitApproval(transferNo, operator);
        return Result.success();
    }

    @PostMapping("/{transferNo}/approve")
    public Result<Void> approve(@PathVariable String transferNo,
                                 @RequestParam String approver,
                                 @RequestParam(required = false) String comment) {
        transferOrderService.approve(transferNo, approver, comment);
        return Result.success();
    }

    @PostMapping("/{transferNo}/reject")
    public Result<Void> reject(@PathVariable String transferNo,
                                @RequestParam String approver,
                                @RequestParam(required = false) String comment) {
        transferOrderService.reject(transferNo, approver, comment);
        return Result.success();
    }

    @PostMapping("/{transferNo}/outbound")
    public Result<Void> outboundSuccess(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.startOutbound(transferNo, operator);
        transferOrderService.outboundSuccess(transferNo, operator);
        return Result.success();
    }

    @PostMapping("/{transferNo}/ship")
    public Result<Void> startShip(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.startShip(transferNo, operator);
        return Result.success();
    }

    @PostMapping("/{transferNo}/inbound")
    public Result<Void> inboundSuccess(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.startInbound(transferNo, operator);
        transferOrderService.inboundSuccess(transferNo, operator);
        return Result.success();
    }

    @PostMapping("/{transferNo}/cancel")
    public Result<Void> cancel(@PathVariable String transferNo, @RequestParam String operator) {
        transferOrderService.cancel(transferNo, operator);
        return Result.success();
    }
}
