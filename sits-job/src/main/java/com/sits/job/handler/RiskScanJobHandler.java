package com.sits.job.handler;

import com.sits.risk.dto.RiskScanResult;
import com.sits.risk.service.RiskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * XXL-Job 处理器：库存风险扫描。
 */
@Component
public class RiskScanJobHandler {

    private static final Logger log = LoggerFactory.getLogger(RiskScanJobHandler.class);

    private final RiskService riskService;

    public RiskScanJobHandler(RiskService riskService) {
        this.riskService = riskService;
    }

    @XxlJob("inventoryRiskScanJob")
    public void execute() {
        log.info(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob start");
        try {
            RiskScanResult result = riskService.scanRisks();
            if (result.isSuccess()) {
                log.info(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob done, " +
                        "scanBatchNo={}, skuCount={}, riskCount={}, cost={}ms",
                        result.getScanBatchNo(), result.getTotalSkuCount(),
                        result.getTotalRiskCount(), result.getCostMillis());
            } else {
                log.warn(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob finished with message: {}", result.getMessage());
            }
        } catch (Exception e) {
            log.error(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob failed", e);
            throw e;
        }
    }
}
