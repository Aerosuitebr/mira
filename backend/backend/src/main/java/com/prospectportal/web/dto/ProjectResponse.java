package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
    UUID id,
    String name,
    String status,
    int progressPercent,
    String clientName,
    Instant dueAt,
    List<ProjectMilestoneResponse> milestones
) {
}
