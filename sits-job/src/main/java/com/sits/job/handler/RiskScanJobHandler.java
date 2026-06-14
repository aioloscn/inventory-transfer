package com.sits.job.handler;

import com.sits.risk.service.RiskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * XXL-Job handler: Inventory Risk Scan.
 *
 * <p>Scheduled to run periodically (e.g. every 30 minutes) to scan
 * all inventory records and detect shortage / overstock risks.
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
            var risks = riskService.scanRisks();
            log.info(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob done, found {} risks", risks.size());
        } catch (Exception e) {
            log.error(">>>>>>>>>>> XXL-Job: inventoryRiskScanJob failed", e);
            throw e;
        }
    }
}
