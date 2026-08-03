package com.prospectportal.module.proposal.event;

import java.math.BigDecimal;
import java.util.UUID;

public record ProposalApprovedEvent(
    UUID tenantId,
    UUID proposalId,
    UUID leadId,
    BigDecimal totalAmount,
    String signerName,
    String signerDocument
) {
}
