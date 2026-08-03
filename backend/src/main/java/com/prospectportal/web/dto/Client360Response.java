package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Client360Response(
    UUID id,
    String legalName,
    String tradeName,
    String document,
    String email,
    String phone,
    String city,
    String state,
    String status,
    String serviceStatus,
    BigDecimal lifetimeValue,
    Instant contractedAt,
    long tenureDays,
    String ownerName,
    List<ClientContactHistoryItem> contactHistory,
    List<ClientProposalHistoryItem> proposals,
    List<ClientServiceItem> services
) {
}
