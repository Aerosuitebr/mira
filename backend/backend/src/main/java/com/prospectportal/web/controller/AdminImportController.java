package com.prospectportal.web.controller;

import com.prospectportal.module.ingestion.dto.ImportJobResponse;
import com.prospectportal.module.ingestion.dto.RfEnrichRequest;
import com.prospectportal.module.ingestion.dto.RfImportRequest;
import com.prospectportal.module.ingestion.dto.WebProbeRequest;
import com.prospectportal.module.ingestion.entity.ImportJob;
import com.prospectportal.module.ingestion.repository.ImportJobRepository;
import com.prospectportal.module.ingestion.rf.RfImportService;
import com.prospectportal.module.enrichment.web.WebContactabilityProbeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private final RfImportService rfImportService;
    private final ImportJobRepository importJobRepository;
    private final WebContactabilityProbeService webContactabilityProbeService;

    public AdminImportController(
        RfImportService rfImportService,
        ImportJobRepository importJobRepository,
        WebContactabilityProbeService webContactabilityProbeService
    ) {
        this.rfImportService = rfImportService;
        this.importJobRepository = importJobRepository;
        this.webContactabilityProbeService = webContactabilityProbeService;
    }

    @PostMapping("/rf/enrich")
    public ImportJobResponse startRfEnrichment(@RequestBody RfEnrichRequest request) {
        ImportJob job = rfImportService.startEnrichment(request.states(), request.geocode(), request.syncElasticsearch());
        rfImportService.runEnrichmentAsync(job.getId(), request.states(), request.geocode(), request.syncElasticsearch());
        return toResponse(job);
    }

    @PostMapping("/rf")
    public ImportJobResponse startRfImport(@RequestBody RfImportRequest request) {
        ImportJob job = rfImportService.startImport(request);
        rfImportService.runImportAsync(job.getId(), request);
        return toResponse(job);
    }

    @PostMapping("/rf/probe-web")
    public ImportJobResponse startWebProbe(@RequestBody WebProbeRequest request) {
        ImportJob job = webContactabilityProbeService.startProbeJob(request.states(), request.limit());
        webContactabilityProbeService.runProbeAsync(job.getId(), request.states(), request.limit());
        return toResponse(job);
    }

    @GetMapping("/rf/status")
    public Map<String, Object> status() {
        List<ImportJobResponse> jobs = importJobRepository.findTop10ByOrderByCreatedAtDesc()
            .stream()
            .map(this::toResponse)
            .toList();
        ImportJobResponse running = jobs.stream().filter(j -> "RUNNING".equals(j.status())).findFirst().orElse(null);
        return Map.of(
            "running", running != null,
            "recentJobs", jobs
        );
    }

    @GetMapping("/data-paths")
    public Map<String, String> dataPaths(
        @org.springframework.beans.factory.annotation.Value("${app.data.root}") String root,
        @org.springframework.beans.factory.annotation.Value("${app.data.rf-extracted}") String extracted
    ) {
        return Map.of(
            "dataRoot", root,
            "rfExtracted", extracted,
            "hint", "Baixe ZIPs com scripts/download-rf.ps1 e extraia com extract-rf.ps1"
        );
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
