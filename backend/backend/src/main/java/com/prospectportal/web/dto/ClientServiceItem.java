package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientServiceItem(
    UUID id,
    String name,
    String status,
    int progressPercent,
    Instant startedAt,
    Instant completedAt
) {
}
