package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record BulkCampaignResponse(
    UUID id,
    String name,
    String channel,
    String status,
    int sentCount,
    int waSent,
    int emailSent,
    int failedCount,
    String detail,
    Instant createdAt
) {
}
