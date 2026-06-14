package com.sits.risk.controller;

import com.sits.common.base.Result;
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.entity.InventoryRisk;
import com.sits.risk.service.RiskService;
import com.sits.transfer.entity.TransferSuggestion;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Risk and suggestion API.
 */
@RestController
@RequestMapping("/api/risks")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    // -- Risk scan --

    @PostMapping("/scan")
    public Result<List<InventoryRisk>> scanRisks() {
        return Result.success(riskService.scanRisks());
    }

    @GetMapping
    public Result<List<InventoryRisk>> listRisks(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String status) {
        return Result.success(riskService.listRisks(skuId, warehouseId, riskType, status));
    }

    @PutMapping("/{riskId}/status")
    public Result<Void> updateRiskStatus(@PathVariable Long riskId, @RequestParam String status) {
        riskService.updateRiskStatus(riskId, status);
        return Result.success();
    }

    // -- Transfer suggestions --

    @PostMapping("/suggestions/generate")
    public Result<List<TransferSuggestion>> generateSuggestions() {
        return Result.success(riskService.generateSuggestions());
    }

    @GetMapping("/suggestions")
    public Result<List<TransferSuggestion>> listSuggestions(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String status) {
        return Result.success(riskService.listSuggestions(skuId, status));
    }

    @PutMapping("/suggestions/{suggestionId}/confirm")
    public Result<Void> confirmSuggestion(@PathVariable Long suggestionId) {
        riskService.confirmSuggestion(suggestionId);
        return Result.success();
    }

    @PutMapping("/suggestions/{suggestionId}/reject")
    public Result<Void> rejectSuggestion(@PathVariable Long suggestionId) {
        riskService.rejectSuggestion(suggestionId);
        return Result.success();
    }

    @PutMapping("/suggestions/{suggestionId}/convert")
    public Result<Void> markSuggestionConverted(@PathVariable Long suggestionId) {
        riskService.markSuggestionConverted(suggestionId);
        return Result.success();
    }

    // -- Compensation tasks --

    @GetMapping("/compensation-tasks")
    public Result<List<CompensationTask>> listPendingCompensationTasks(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(riskService.listPendingCompensationTasks(limit));
    }

    @PutMapping("/compensation-tasks/{taskId}")
    public Result<Void> updateCompensationTask(
            @PathVariable Long taskId,
            @RequestParam String status,
            @RequestParam(required = false) String errorMessage) {
        riskService.updateCompensationTask(taskId, status, errorMessage);
        return Result.success();
    }
}
