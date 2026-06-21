package com.sits.job.handler;

import com.sits.risk.service.RiskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * XXL-Job 处理器：调拨建议生成。
 *
 * <p>在风险扫描之后执行，为未解决的风险生成调拨建议。
 */
@Component
public class SuggestionGenerateJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SuggestionGenerateJobHandler.class);

    private final RiskService riskService;

    public SuggestionGenerateJobHandler(RiskService riskService) {
        this.riskService = riskService;
    }

    @XxlJob("transferSuggestionGenerateJob")
    public void execute() {
        log.info(">>>>>>>>>>> XXL-Job: transferSuggestionGenerateJob start");
        try {
            var suggestions = riskService.generateSuggestions();
            log.info(">>>>>>>>>>> XXL-Job: transferSuggestionGenerateJob done, generated {} suggestions",
                    suggestions.size());
        } catch (Exception e) {
            log.error(">>>>>>>>>>> XXL-Job: transferSuggestionGenerateJob failed", e);
            throw e;
        }
    }
}
