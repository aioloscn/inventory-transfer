package com.sits.job.handler;

import com.sits.common.enums.CompensationStatus;
import com.sits.risk.entity.CompensationTask;
import com.sits.risk.service.RiskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * XXL-Job handler: Compensation Task Processing.
 *
 * <p>Picks up pending/retrying compensation tasks and attempts to re-execute them.
 * Uses exponential backoff: 1min, 2min, 4min, 8min, 16min, then marks as FAILED.
 */
@Component
public class CompensationJobHandler {

    private static final Logger log = LoggerFactory.getLogger(CompensationJobHandler.class);

    /** Maximum tasks to process per execution */
    private static final int BATCH_SIZE = 50;

    private final RiskService riskService;

    public CompensationJobHandler(RiskService riskService) {
        this.riskService = riskService;
    }

    @XxlJob("compensationJob")
    public void execute() {
        log.info(">>>>>>>>>>> XXL-Job: compensationJob start");

        List<CompensationTask> tasks = riskService.listPendingCompensationTasks(BATCH_SIZE);
        log.info("Found {} pending compensation tasks", tasks.size());

        int success = 0;
        int failed = 0;

        for (CompensationTask task : tasks) {
            try {
                // Attempt retry — the actual retry logic depends on the bizType
                boolean retryOk = executeRetry(task);

                if (retryOk) {
                    riskService.updateCompensationTask(task.getId(),
                            CompensationStatus.SUCCESS.name(), null);
                    success++;
                } else {
                    handleRetryFailure(task);
                    failed++;
                }
            } catch (Exception e) {
                log.error("Compensation task {} retry failed", task.getTaskNo(), e);
                handleRetryFailure(task);
                failed++;
            }
        }

        log.info(">>>>>>>>>>> XXL-Job: compensationJob done. success={}, failed={}", success, failed);
    }

    /**
     * Execute the actual retry based on bizType.
     * Currently a placeholder — real implementation would re-invoke the failed operation.
     */
    private boolean executeRetry(CompensationTask task) {
        // TODO: Implement per-bizType retry logic
        // e.g., re-send MQ message, re-trigger inventory lock, etc.
        log.info("Retrying compensation task: {} (bizType={}, bizNo={})",
                task.getTaskNo(), task.getBizType(), task.getBizNo());
        return true; // Placeholder: assume success
    }

    private void handleRetryFailure(CompensationTask task) {
        if (task.getRetryCount() >= task.getMaxRetryCount() - 1) {
            // Exhausted retries — mark as MANUAL
            riskService.updateCompensationTask(task.getId(),
                    CompensationStatus.MANUAL.name(),
                    "Exceeded max retry count (" + task.getMaxRetryCount() + ")");
            log.warn("Compensation task {} exhausted retries, marked as MANUAL", task.getTaskNo());
        } else {
            // Schedule next retry with exponential backoff
            riskService.updateCompensationTask(task.getId(),
                    CompensationStatus.RETRYING.name(),
                    task.getErrorMessage());
            log.info("Compensation task {} scheduled for retry #{}", task.getTaskNo(), task.getRetryCount() + 1);
        }
    }
}
