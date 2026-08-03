package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record CreateProposalRequest(
    UUID leadId,
    UUID clientId,
    UUID companyId,
    String title,
    String paymentTerms,
    Integer validityDays,
    List<ProposalItemRequest> items
) {
}
