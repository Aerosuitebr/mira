package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record ProjectMilestoneResponse(
    UUID id,
    String title,
    String status,
    int position,
    boolean requiresClientApproval
) {
}
