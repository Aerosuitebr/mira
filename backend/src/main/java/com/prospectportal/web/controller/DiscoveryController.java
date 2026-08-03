package com.prospectportal.web.controller;

import com.prospectportal.module.discovery.CnaeCatalogService;
import com.prospectportal.module.discovery.DiscoveryService;
import com.prospectportal.web.dto.CnaeActivityOptionResponse;
import com.prospectportal.web.dto.CnaeSectionCatalogResponse;
import com.prospectportal.web.dto.CompanyResponse;
import com.prospectportal.web.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.prospectportal.web.dto.RefineCoordinatesRequest;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final CnaeCatalogService cnaeCatalogService;

    public DiscoveryController(DiscoveryService discoveryService, CnaeCatalogService cnaeCatalogService) {
        this.discoveryService = discoveryService;
        this.cnaeCatalogService = cnaeCatalogService;
    }

    @GetMapping("/cnaes")
    public List<CnaeSectionCatalogResponse> listCnaes() {
        return cnaeCatalogService.getCatalog();
    }

    @GetMapping("/cnaes/search")
    public List<CnaeActivityOptionResponse> searchCnaes(
        @RequestParam String q,
        @RequestParam(defaultValue = "40") int limit
    ) {
        return cnaeCatalogService.searchSubclasses(q, limit);
    }

    @GetMapping("/cnaes/subclasses")
    public List<CnaeActivityOptionResponse> listCnaeSubclasses(
        @RequestParam String prefix,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return cnaeCatalogService.listSubclassesByPrefix(prefix, limit);
    }

    @GetMapping("/companies")
    public PageResponse<CompanyResponse> searchCompanies(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String cnae,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String city,
        @RequestParam(required = false) String revenue,
        @RequestParam(defaultValue = "true") boolean activeOnly,
        @RequestParam(defaultValue = "false") boolean contactableOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return discoveryService.search(keyword, cnae, state, city, revenue, activeOnly, contactableOnly, page, size);
    }

    @GetMapping("/companies/geo")
    public List<CompanyResponse> searchByGeo(
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "5") double radiusKm,
        @RequestParam(required = false) String cnae,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return discoveryService.searchByRadius(latitude, longitude, radiusKm, cnae, limit);
    }

    @GetMapping("/companies/by-ids")
    public List<CompanyResponse> getCompaniesByIds(@RequestParam String ids) {
        return discoveryService.findByIds(ids);
    }

    @PostMapping("/companies/refine-coordinates")
    public List<CompanyResponse> refineCoordinates(@RequestBody RefineCoordinatesRequest request) {
        if (request == null || request.companyIds() == null) {
            return List.of();
        }
        return discoveryService.refineMapCoordinates(request.companyIds());
    }
}
