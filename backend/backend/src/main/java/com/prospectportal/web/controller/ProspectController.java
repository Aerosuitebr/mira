package com.prospectportal.web.controller;

import com.prospectportal.module.prospect.ProspectAutomationService;
import com.prospectportal.web.dto.ProspectJobRequest;
import com.prospectportal.web.dto.ProspectJobResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/prospect")
public class ProspectController {

    private final ProspectAutomationService prospectAutomationService;

    public ProspectController(ProspectAutomationService prospectAutomationService) {
        this.prospectAutomationService = prospectAutomationService;
    }

    @PostMapping("/jobs")
    public ProspectJobResponse start(@RequestBody ProspectJobRequest request) {
        return prospectAutomationService.start(request);
    }

    @GetMapping("/jobs")
    public List<ProspectJobResponse> list() {
        return prospectAutomationService.listJobs();
    }

    @GetMapping("/jobs/{id}")
    public ProspectJobResponse get(@PathVariable UUID id) {
        return prospectAutomationService.getJob(id);
    }

    @PostMapping("/jobs/{id}/pause")
    public ProspectJobResponse pause(@PathVariable UUID id) {
        return prospectAutomationService.pause(id);
    }

    @PostMapping("/jobs/{id}/resume")
    public ProspectJobResponse resume(@PathVariable UUID id) {
        return prospectAutomationService.resume(id);
    }
}
