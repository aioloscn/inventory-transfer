package com.sits.risk.dto;

import java.time.LocalDateTime;

/**
 * 风险扫描结果。
 */
public class RiskScanResult {

    private String scanBatchNo;
    private long totalSkuCount;
    private long totalRiskCount;
    private long resolvedRiskCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long costMillis;
    private boolean success;
    private String message;

    public static RiskScanResult success(String scanBatchNo, long totalSkuCount,
                                         long totalRiskCount, long resolvedRiskCount,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        RiskScanResult r = new RiskScanResult();
        r.scanBatchNo = scanBatchNo;
        r.totalSkuCount = totalSkuCount;
        r.totalRiskCount = totalRiskCount;
        r.resolvedRiskCount = resolvedRiskCount;
        r.startTime = startTime;
        r.endTime = endTime;
        r.costMillis = java.time.Duration.between(startTime, endTime).toMillis();
        r.success = true;
        r.message = "扫描完成";
        return r;
    }

    public static RiskScanResult fail(String scanBatchNo, String message) {
        RiskScanResult r = new RiskScanResult();
        r.scanBatchNo = scanBatchNo;
        r.startTime = LocalDateTime.now();
        r.endTime = r.startTime;
        r.costMillis = 0;
        r.success = false;
        r.message = message;
        return r;
    }

    public static RiskScanResult locked() {
        RiskScanResult r = new RiskScanResult();
        r.success = false;
        r.message = "扫描任务正在执行中，请稍后重试";
        return r;
    }

    // -- getters --

    public String getScanBatchNo() { return scanBatchNo; }
    public void setScanBatchNo(String scanBatchNo) { this.scanBatchNo = scanBatchNo; }
    public long getTotalSkuCount() { return totalSkuCount; }
    public void setTotalSkuCount(long totalSkuCount) { this.totalSkuCount = totalSkuCount; }
    public long getTotalRiskCount() { return totalRiskCount; }
    public void setTotalRiskCount(long totalRiskCount) { this.totalRiskCount = totalRiskCount; }
    public long getResolvedRiskCount() { return resolvedRiskCount; }
    public void setResolvedRiskCount(long resolvedRiskCount) { this.resolvedRiskCount = resolvedRiskCount; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public long getCostMillis() { return costMillis; }
    public void setCostMillis(long costMillis) { this.costMillis = costMillis; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
