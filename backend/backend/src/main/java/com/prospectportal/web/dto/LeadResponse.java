package com.prospectportal.web.dto;

import java.util.UUID;

public record LeadResponse(
    UUID id,
    UUID companyId,
    String companyName,
    String status,
    int score
) {
}
