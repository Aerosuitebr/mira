package com.prospectportal.web.dto;

import java.util.List;

public record ProjectColumnResponse(
    String status,
    String label,
    List<ProjectResponse> projects
) {
}
