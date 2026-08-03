package com.prospectportal.web.controller;

import com.prospectportal.module.project.ProjectService;
import com.prospectportal.web.dto.ProjectBoardResponse;
import com.prospectportal.web.dto.ProjectResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/board")
    public ProjectBoardResponse board() {
        return projectService.getDeliveryBoard();
    }

    @PatchMapping("/{id}/status")
    public ProjectResponse updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return projectService.updateStatus(id, body.get("status"));
    }
}
