package com.prospectportal.module.search;

import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class CompanyReindexService {

    private static final Logger log = LoggerFactory.getLogger(CompanyReindexService.class);

    private final CompanyIndexingService indexingService;
    private final ImportJobRepository importJobRepository;

    public CompanyReindexService(CompanyIndexingService indexingService, ImportJobRepository importJobRepository) {
        this.indexingService = indexingService;
        this.importJobRepository = importJobRepository;
    }

    public ImportJob startReindexJob(List<String> states, boolean recreateIndex) {
        importJobRepository.findFirstByStatusOrderByCreatedAtDesc("RUNNING").ifPresent(stale -> {
            if ("ES_REINDEX".equals(stale.getJobType())) {
                stale.setStatus("FAILED");
                stale.setErrorMessage("Substituído por novo job de reindexação");
                stale.setFinishedAt(Instant.now());
                importJobRepository.save(stale);
            }
        });

        ImportJob job = new ImportJob();
        job.setJobType("ES_REINDEX");
        job.setStatus("PENDING");
        job.setParamsJson("{\"states\":" + states + ",\"recreateIndex\":" + recreateIndex + "}");
        job.setProcessedRows(0);
        job.setInsertedRows(0);
        job.setSkippedRows(0);
        job.setCreatedAt(Instant.now());
        return importJobRepository.save(job);
    }

    @Async("importTaskExecutor")
    public void runReindexAsync(UUID jobId, List<String> states, boolean recreateIndex) {
        ImportJob job = importJobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setStartedAt(Instant.now());
        importJobRepository.save(job);

        Set<String> stateSet = new HashSet<>(states.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());

        try {
            if (recreateIndex) {
                indexingService.recreateIndex();
            } else {
                indexingService.ensureIndex();
            }

            long indexed = indexingService.reindexByStates(stateSet, job);
            job.setInsertedRows(indexed);
            job.setSkippedRows(indexed);
            job.setStatus("COMPLETED");
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
            log.info("Reindexação Elasticsearch concluída: {} documentos", indexed);
        } catch (Exception ex) {
            log.error("Falha na reindexação Elasticsearch", ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setFinishedAt(Instant.now());
            importJobRepository.save(job);
        }
    }
}
