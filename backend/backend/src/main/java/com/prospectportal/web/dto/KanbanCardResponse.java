package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KanbanCardResponse(
    UUID id,
    UUID leadId,
    String title,
    String companyName,
    String city,
    String state,
    BigDecimal valueAmount,
    int position,
    Instant createdAt,
    String ownerName
) {
}
