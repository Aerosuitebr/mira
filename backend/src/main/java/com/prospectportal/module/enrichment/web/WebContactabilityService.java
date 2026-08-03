package com.prospectportal.module.enrichment.web;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WebContactabilityService {

    private final WebPresenceEnricher webPresenceEnricher;
    private final CompanyRepository companyRepository;

    public WebContactabilityService(WebPresenceEnricher webPresenceEnricher, CompanyRepository companyRepository) {
        this.webPresenceEnricher = webPresenceEnricher;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public boolean probeAndPersist(Company company) {
        WebPresenceEnricher.ContactabilityAssessment assessment = webPresenceEnricher.assessContactability(company);
        applyAssessment(company, assessment);
        companyRepository.save(company);
        return assessment.contactable();
    }

    @Transactional
    public void applyFromEnrichment(Company company, WebEnrichmentSignals web) {
        boolean hasSite = web.getWebsiteUrl() != null && !web.getWebsiteUrl().isBlank();
        boolean contactable = hasSite && (web.hasAnyContact() || hasSite);
        String status = !hasSite ? "NO_SITE" : (web.hasAnyContact() ? "CONTACTABLE" : "LOW_SIGNAL");

        company.setWebContactable(contactable);
        company.setWebProbeStatus(status);
        company.setWebProbedAt(Instant.now());
        if (hasSite) {
            company.setWebsite(web.getWebsiteUrl());
        }
        companyRepository.save(company);
    }

    private void applyAssessment(Company company, WebPresenceEnricher.ContactabilityAssessment assessment) {
        company.setWebContactable(assessment.contactable());
        company.setWebProbeStatus(assessment.status());
        company.setWebProbedAt(Instant.now());
        if (assessment.websiteUrl() != null) {
            company.setWebsite(assessment.websiteUrl());
        }
    }
}
