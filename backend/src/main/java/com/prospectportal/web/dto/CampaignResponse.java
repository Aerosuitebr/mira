package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
    UUID id,
    String name,
    String channel,
    String status,
    int sentCount,
    Instant createdAt
) {
}
