package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProposalItemResponse(
    UUID id,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    int sortOrder
) {
}
