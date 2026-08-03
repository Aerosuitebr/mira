package com.prospectportal.web.controller;

import com.prospectportal.module.client.ClientService;
import com.prospectportal.web.dto.Client360Response;
import com.prospectportal.web.dto.ClientListItemResponse;
import com.prospectportal.web.dto.CreateClientRequest;
import com.prospectportal.web.dto.PortfolioStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/stats")
    public PortfolioStatsResponse stats() {
        return clientService.portfolioStats();
    }

    @PostMapping
    public ClientListItemResponse create(@RequestBody CreateClientRequest request) {
        return clientService.createManual(request);
    }

    @GetMapping
    public List<ClientListItemResponse> list(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String serviceStatus,
        @RequestParam(required = false) BigDecimal minLtv,
        @RequestParam(required = false) Integer tenureDays
    ) {
        return clientService.listPortfolio(status, serviceStatus, minLtv, tenureDays);
    }

    @GetMapping("/{id}")
    public Client360Response get(@PathVariable UUID id) {
        return clientService.getClient360(id);
    }
}
