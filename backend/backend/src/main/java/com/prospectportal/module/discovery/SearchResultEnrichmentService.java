package com.prospectportal.module.discovery;

import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.module.geo.GeocodingService;
import com.prospectportal.module.search.CompanyIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SearchResultEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(SearchResultEnrichmentService.class);

    private final CompanyRepository companyRepository;
    private final GeocodingService geocodingService;
    private final ObjectProvider<CompanyIndexingService> indexingService;

    public SearchResultEnrichmentService(
        CompanyRepository companyRepository,
        GeocodingService geocodingService,
        ObjectProvider<CompanyIndexingService> indexingService
    ) {
        this.companyRepository = companyRepository;
        this.geocodingService = geocodingService;
        this.indexingService = indexingService;
    }

    @Async("searchEnrichmentExecutor")
    public void enrichSearchResults(List<UUID> companyIds) {
        indexingService.ifAvailable(service -> service.indexByIds(companyIds));

        int geocoded = 0;
        for (Company company : companyRepository.findAllById(companyIds)) {
            if ("EXACT".equals(company.getLocationPrecision())) {
                continue;
            }
            if (!geocodingService.hasGeocodableAddress(company.getStreet(), company.getCity())) {
                continue;
            }
            try {
                Optional<GeocodingService.MapCoordinate> resolved = geocodingService.refineWithNominatim(
                    company.getId(),
                    company.getStreet(),
                    company.getCity(),
                    company.getState(),
                    company.getZipCode()
                );
                if (resolved.isPresent()) {
                    geocoded++;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (geocoded > 0) {
            indexingService.ifAvailable(service -> service.indexByIds(companyIds));
        }

        log.info(
            "Enriquecimento pós-busca: {} empresas processadas, {} geocodificadas com endereço exato",
            companyIds.size(),
            geocoded
        );
    }
}
