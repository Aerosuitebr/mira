package com.prospectportal.module.prospect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Isolado para garantir proxy Spring no {@code @Async}.
 */
@Component
public class ProspectJobPreparer {

    private static final Logger log = LoggerFactory.getLogger(ProspectJobPreparer.class);

    private final ProspectAutomationService automationService;

    public ProspectJobPreparer(ProspectAutomationService automationService) {
        this.automationService = automationService;
    }

    @Async("prospectTaskExecutor")
    public void prepareAsync(UUID jobId) {
        try {
            automationService.prepareJob(jobId);
        } catch (Exception ex) {
            log.error("Falha ao preparar job {}: {}", jobId, ex.getMessage(), ex);
            automationService.markFailed(jobId, ex.getMessage());
        }
    }
}
