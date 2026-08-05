package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ApproachStatusResponse(
    UUID companyId,
    UUID leadId,
    String leadStatus,
    boolean approached,
    String lastChannel,
    String lastProvider,
    String lastRecipient,
    Instant lastSentAt,
    String lastErrorDetail,
    boolean wasFallback,
    String lastCampaignName
) {
}
