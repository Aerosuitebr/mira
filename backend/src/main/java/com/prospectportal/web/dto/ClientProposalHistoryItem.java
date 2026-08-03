package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClientProposalHistoryItem(
    UUID id,
    String title,
    String status,
    BigDecimal totalAmount,
    Instant createdAt,
    Instant approvedAt,
    Instant rejectedAt
) {
}
