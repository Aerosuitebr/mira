package com.prospectportal.module.prospect;

import com.prospectportal.module.prospect.entity.ProspectJob;
import com.prospectportal.module.prospect.repository.ProspectJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProspectDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(ProspectDispatchWorker.class);

    private final ProspectJobRepository jobRepository;
    private final ProspectAutomationService automationService;

    public ProspectDispatchWorker(
        ProspectJobRepository jobRepository,
        ProspectAutomationService automationService
    ) {
        this.jobRepository = jobRepository;
        this.automationService = automationService;
    }

    @Scheduled(fixedDelayString = "${app.outreach.dispatch-interval-ms:20000}")
    public void tick() {
        List<ProspectJob> running = jobRepository.findRunningJobs();
        if (running.isEmpty()) {
            return;
        }
        for (ProspectJob job : running) {
            try {
                automationService.dispatchNext(job);
            } catch (Exception ex) {
                log.warn("Erro no dispatch do job {}: {}", job.getId(), ex.getMessage());
            }
            // Um envio por tick global para reforçar anti-ban
            break;
        }
    }
}
