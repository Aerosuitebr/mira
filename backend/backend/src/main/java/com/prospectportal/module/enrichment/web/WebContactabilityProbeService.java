package com.prospectportal.module.enrichment.web;

import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WebContactabilityProbeService {

    private static final Logger log = LoggerFactory.getLogger(WebContactabilityProbeService.class);
    private static final int BATCH_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;
    private final CompanyRepository companyRepository;
    private final WebContactabilityService webContactabilityService;
    private final ImportJobRepository importJobRepository;

    public WebContactabilityProbeService(
        JdbcTemplate jdbcTemplate,
        CompanyRepository companyRepository,
        WebContactabilityService webContactabilityService,
        ImportJobRepository importJobRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.companyRepository = companyRepository;
        this.webContactabilityService = webContactabilityService;
        this.importJobRepository = importJobRepository;
    }

    public ImportJob startProbeJob(List<String> states, int limit) {
        importJobRepository.findFirstByStatusOrderByCreatedAtDesc("RUNNING").ifPresent(stale -> {
            stale.setStatus("FAILED");
            stale.setErrorMessage("Substituído por nova varredura web");
            stale.setFinishedAt(Instant.now());
            importJobRepository.save(stale);
        });

        ImportJob job = new ImportJob();
        job.setJobType("WEB_PROBE");
        job.setStatus("PENDING");
        job.setParamsJson("{\"states\":" + states + ",\"limit\":" + limit + "}");
        job.setProcessedRows(0);
        job.setInsertedRows(0);
        job.setSkippedRows(0);
        job.setCreatedAt(Instant.now());
        return importJobRepository.save(job);
    }

    @Async("importTaskExecutor")
    public void runProbeAsync(UUID jobId, List<String> states, int limit) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        importJobRepository.save(job);

        Set<String> stateSet = new HashSet<>(states.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());
        int cap = limit <= 0 ? BATCH_LIMIT : Math.min(limit, 5000);
        String inClause = stateSet.stream().map(s -> "'" + s + "'").reduce((a, b) -> a + "," + b).orElse("''");

        try {
            List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM companies
                WHERE UPPER(state) IN (%s)
                  AND COALESCE(registration_status, '02') = '02'
                  AND web_probed_at IS NULL
                ORDER BY created_at
                LIMIT %d
                """.formatted(inClause, cap)
            );

            log.info("Varredura web: {} empresas pendentes (UFs: {})", pending.size(), stateSet);
            int contactable = 0;

            for (Map<String, Object> row : pending) {
                UUID companyId = UUID.fromString(row.get("id").toString());
                var company = companyRepository.findById(companyId).orElse(null);
                if (company == null) {
                    continue;
                }

                job.setProcessedRows(job.getProcessedRows() + 1);
                try {
                    if (webContactabilityService.probeAndPersist(company)) {
                        contactable++;
                        job.setInsertedRows(job.getInsertedRows() + 1);
                    } else {
                        job.setSkippedRows(job.getSkippedRows() + 1);
                    }
                } catch (Exception ex) {
                    log.warn("Falha ao avaliar site de {}: {}", company.getLegalName(), ex.getMessage());
                    job.setSkippedRows(job.getSkippedRows() + 1);
                }

                if (job.getProcessedRows() % 50 == 0) {
                    importJobRepository.save(job);
                }
            }

            job.setStatus("COMPLETED");
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
            log.info("Varredura web concluída: {} processadas, {} contatáveis", job.getProcessedRows(), contactable);
        } catch (Exception ex) {
            log.error("Falha na varredura web", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
        }
    }
}
