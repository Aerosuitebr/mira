package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProposalResponse(
    UUID id,
    UUID leadId,
    String title,
    BigDecimal totalAmount,
    String status,
    String paymentTerms,
    int validityDays,
    UUID approvalToken,
    String approvalUrl,
    Instant createdAt,
    List<ProposalItemResponse> items
) {
}
