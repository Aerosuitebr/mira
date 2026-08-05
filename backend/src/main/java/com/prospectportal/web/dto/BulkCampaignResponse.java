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
    Instant createdAt,
    java.util.List<String> nonWhatsApp
) {
    public BulkCampaignResponse(
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
        this(id, name, channel, status, sentCount, waSent, emailSent, failedCount, detail, createdAt, java.util.List.of());
    }
}
