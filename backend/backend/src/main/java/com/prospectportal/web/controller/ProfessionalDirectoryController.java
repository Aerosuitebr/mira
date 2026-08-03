package com.prospectportal.web.controller;

import com.prospectportal.module.professional.ProfessionalDirectoryService;
import com.prospectportal.web.dto.ProfessionalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@RestController
@RequestMapping("/api/professionals")
public class ProfessionalDirectoryController {
    private final ProfessionalDirectoryService service;

    public ProfessionalDirectoryController(ProfessionalDirectoryService service) { this.service = service; }

    @GetMapping("/search")
    public List<ProfessionalResponse> search(
        @RequestParam String query,
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "10") double radiusKm
    ) {
        return service.search(query, latitude, longitude, radiusKm);
    }

    @GetMapping("/location")
    public ProfessionalDirectoryService.SearchPoint resolveLocation(@RequestParam String location) {
        return service.resolveSearchPoint(location)
            .orElseThrow(() -> new IllegalArgumentException("Local não encontrado. Informe bairro e cidade ou um CEP válido."));
    }

    @PostMapping("/{id}/contact")
    public ProfessionalDirectoryService.ContactResult claimContact(
        @org.springframework.web.bind.annotation.PathVariable java.util.UUID id,
        @RequestParam String channel
    ) { return service.claimContact(id, channel); }
}
