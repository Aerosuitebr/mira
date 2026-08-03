package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProposalItemRequest(
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice
) {
}
