package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record OutreachMessageHistoryItem(
    UUID id,
    UUID campaignId,
    String campaignName,
    String channel,
    String provider,
    String recipient,
    String status,
    String subject,
    String bodyPreview,
    Instant sentAt,
    Instant createdAt,
    String errorDetail,
    boolean wasFallback
) {
}
