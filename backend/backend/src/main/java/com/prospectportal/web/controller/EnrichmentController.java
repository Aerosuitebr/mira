package com.prospectportal.web.controller;

import com.prospectportal.module.enrichment.EnrichmentService;
import com.prospectportal.web.dto.ContactResponse;
import com.prospectportal.web.dto.EnrichRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrichment")
public class EnrichmentController {

    private final EnrichmentService enrichmentService;

    public EnrichmentController(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @GetMapping("/companies/{companyId}/contacts")
    public List<ContactResponse> listContacts(@PathVariable UUID companyId) {
        return enrichmentService.listContacts(companyId);
    }

    @PostMapping("/enrich")
    public List<ContactResponse> enrich(@RequestBody EnrichRequest request) {
        return enrichmentService.enrichCompanies(request.companyIds(), request.isForceRefresh());
    }
}
