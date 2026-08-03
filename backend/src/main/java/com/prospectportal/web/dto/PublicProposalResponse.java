package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicProposalResponse(
    String companyName,
    String title,
    BigDecimal totalAmount,
    String paymentTerms,
    Instant expiresAt,
    String status,
    List<ProposalItemResponse> items
) {
}
