package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
    UUID id,
    UUID companyId,
    String companyName,
    String alertType,
    String title,
    String description,
    boolean read,
    Instant triggeredAt
) {
}
