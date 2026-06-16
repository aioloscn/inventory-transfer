package com.sits.risk.controller;

import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
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

    @GetMapping("/page")
    public Result<PageResult<InventoryRisk>> pageRisks(
            PageQuery pageQuery,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status) {
        return Result.success(riskService.pageRisks(pageQuery, skuId, warehouseId, riskType, riskLevel, status));
    }

    @GetMapping("/{riskId}")
    public Result<InventoryRisk> getRiskDetail(@PathVariable Long riskId) {
        return Result.success(riskService.getRiskById(riskId));
    }

    @PutMapping("/{riskId}/status")
    public Result<Void> updateRiskStatus(@PathVariable Long riskId, @RequestParam String status) {
        riskService.updateRiskStatus(riskId, status);
        return Result.success();
    }

    @PutMapping("/{riskId}/process")
    public Result<Void> markRiskProcessing(@PathVariable Long riskId) {
        riskService.updateRiskStatus(riskId, com.sits.common.enums.RiskStatus.PROCESSING.name());
        return Result.success();
    }

    @PutMapping("/{riskId}/resolve")
    public Result<Void> markRiskResolved(@PathVariable Long riskId) {
        riskService.updateRiskStatus(riskId, com.sits.common.enums.RiskStatus.RESOLVED.name());
        return Result.success();
    }

    @PutMapping("/{riskId}/ignore")
    public Result<Void> markRiskIgnored(@PathVariable Long riskId) {
        riskService.updateRiskStatus(riskId, com.sits.common.enums.RiskStatus.IGNORED.name());
        return Result.success();
    }

    // -- Transfer suggestions --

    @PostMapping("/suggestions/generate")
    public Result<List<TransferSuggestion>> generateSuggestions() {
        return Result.success(riskService.generateSuggestions());
    }

    @GetMapping("/suggestions/page")
    public Result<PageResult<TransferSuggestion>> pageSuggestions(
            PageQuery pageQuery,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String status) {
        return Result.success(riskService.pageSuggestions(pageQuery, skuId, status));
    }

    @GetMapping("/suggestions/{suggestionId}")
    public Result<TransferSuggestion> getSuggestionDetail(@PathVariable Long suggestionId) {
        return Result.success(riskService.getSuggestionById(suggestionId));
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

    @PostMapping("/suggestions/{suggestionId}/convert")
    public Result<String> convertToTransferOrder(@PathVariable Long suggestionId) {
        return Result.success(riskService.convertToTransferOrder(suggestionId));
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
