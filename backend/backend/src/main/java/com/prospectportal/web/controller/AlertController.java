package com.prospectportal.web.controller;

import com.prospectportal.module.alerts.AlertService;
import com.prospectportal.web.dto.AlertResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> list() {
        return alertService.listAlerts();
    }

    @PatchMapping("/{alertId}/read")
    public AlertResponse markRead(@PathVariable UUID alertId) {
        return alertService.markAsRead(alertId);
    }
}
