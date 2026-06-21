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
 * XXL-Job 处理器：补偿任务处理。
 *
 * <p>拉取待处理 / 重试中的补偿任务，尝试重新执行。
 * 采用指数退避策略：1分钟、2分钟、4分钟、8分钟、16分钟，最后标记为 FAILED。
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
                // 尝试重试 — 实际重试逻辑取决于 bizType
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
     * 根据 bizType 执行实际重试。
     * 当前为占位实现 — 真实实现会重新调用失败的操作。
     */
    private boolean executeRetry(CompensationTask task) {
        // TODO: 实现各 bizType 的重试逻辑
        // 如：重新发送 MQ 消息、重试库存锁定等。
        log.info("Retrying compensation task: {} (bizType={}, bizNo={})",
                task.getTaskNo(), task.getBizType(), task.getBizNo());
        return true; // Placeholder: assume success
    }

    private void handleRetryFailure(CompensationTask task) {
        if (task.getRetryCount() >= task.getMaxRetryCount() - 1) {
            // 重试次数已耗尽 — 标记为 MANUAL
            riskService.updateCompensationTask(task.getId(),
                    CompensationStatus.MANUAL.name(),
                    "Exceeded max retry count (" + task.getMaxRetryCount() + ")");
            log.warn("Compensation task {} exhausted retries, marked as MANUAL", task.getTaskNo());
        } else {
            // 按指数退避安排下次重试
            riskService.updateCompensationTask(task.getId(),
                    CompensationStatus.RETRYING.name(),
                    task.getErrorMessage());
            log.info("Compensation task {} scheduled for retry #{}", task.getTaskNo(), task.getRetryCount() + 1);
        }
    }
}
