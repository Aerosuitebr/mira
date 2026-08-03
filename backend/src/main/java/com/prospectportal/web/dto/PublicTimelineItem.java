package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.List;

public record PublicTimelineItem(
    String title,
    String description,
    String status,
    Instant completedAt,
    boolean requiresClientApproval,
    Instant clientApprovedAt
) {
}
