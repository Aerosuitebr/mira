package com.prospectportal.module.crm.event;

import java.math.BigDecimal;
import java.util.UUID;

public record DealWonEvent(
    UUID tenantId,
    UUID leadId,
    UUID clientId,
    UUID cardId,
    BigDecimal dealValue
) {
}
