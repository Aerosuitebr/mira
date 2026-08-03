package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientListItemResponse(
    UUID id,
    String displayName,
    String document,
    String city,
    String state,
    String status,
    String serviceStatus,
    BigDecimal lifetimeValue,
    long tenureDays,
    String ownerName,
    String email,
    String phone
) {
}
