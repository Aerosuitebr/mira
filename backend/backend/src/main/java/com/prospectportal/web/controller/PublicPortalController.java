package com.prospectportal.web.controller;

import com.prospectportal.module.project.ProjectService;
import com.prospectportal.web.dto.PublicPortalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/portal")
public class PublicPortalController {

    private final ProjectService projectService;

    public PublicPortalController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/{token}")
    public PublicPortalResponse get(@PathVariable UUID token) {
        return projectService.getPublicPortal(token);
    }
}
