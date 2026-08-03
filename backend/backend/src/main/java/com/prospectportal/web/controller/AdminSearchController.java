package com.prospectportal.web.controller;

import com.prospectportal.module.ingestion.dto.ImportJobResponse;
import com.prospectportal.module.ingestion.dto.ReindexRequest;
import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import com.prospectportal.module.search.CompanyIndexingService;
import com.prospectportal.module.search.CompanyReindexService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/search")
public class AdminSearchController {

    private final ObjectProvider<CompanyIndexingService> indexingService;
    private final ObjectProvider<CompanyReindexService> reindexService;
    private final ImportJobRepository importJobRepository;
    private final boolean elasticsearchEnabled;
    private final String indexName;

    public AdminSearchController(
        ObjectProvider<CompanyIndexingService> indexingService,
        ObjectProvider<CompanyReindexService> reindexService,
        ImportJobRepository importJobRepository,
        @Value("${app.elasticsearch.enabled:true}") boolean elasticsearchEnabled,
        @Value("${app.elasticsearch.index:companies}") String indexName
    ) {
        this.indexingService = indexingService;
        this.reindexService = reindexService;
        this.importJobRepository = importJobRepository;
        this.elasticsearchEnabled = elasticsearchEnabled;
        this.indexName = indexName;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        CompanyIndexingService indexer = indexingService.getIfAvailable();
        long indexedDocuments = indexer != null ? indexer.countIndexedDocuments() : 0;
        long postgresCompanies = indexer != null ? indexer.countPostgresCompanies() : 0;
        ImportJob running = importJobRepository.findFirstByStatusOrderByCreatedAtDesc("RUNNING")
            .filter(job -> "ES_REINDEX".equals(job.getJobType()))
            .orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("elasticsearchEnabled", elasticsearchEnabled && indexer != null);
        body.put("indexName", indexName);
        body.put("indexedDocuments", indexedDocuments);
        body.put("postgresCompanies", postgresCompanies);
        body.put("inSync", postgresCompanies > 0 && indexedDocuments >= postgresCompanies);
        body.put("reindexRunning", running != null);
        if (running != null) {
            body.put("runningJob", toResponse(running));
        }
        return body;
    }

    @PostMapping("/reindex")
    public ImportJobResponse startReindex(@RequestBody ReindexRequest request) {
        CompanyReindexService service = reindexService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Elasticsearch desabilitado (app.elasticsearch.enabled=false)");
        }
        ImportJob job = service.startReindexJob(request.states(), request.recreateIndex());
        service.runReindexAsync(job.getId(), request.states(), request.recreateIndex());
        return toResponse(job);
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
            job.getId(),
            job.getJobType(),
            job.getStatus(),
            job.getProcessedRows(),
            job.getInsertedRows(),
            job.getSkippedRows(),
            job.getErrorMessage(),
            job.getStartedAt(),
            job.getFinishedAt(),
            job.getCreatedAt()
        );
    }
}
