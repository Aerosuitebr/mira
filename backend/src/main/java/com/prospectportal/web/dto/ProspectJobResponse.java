package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ProspectJobResponse(
    UUID id,
    String name,
    String cnae,
    String state,
    String city,
    String keyword,
    int companyLimit,
    String status,
    boolean testMode,
    boolean dryRun,
    int foundCount,
    int enrichedCount,
    int queuedCount,
    int waSent,
    int emailSent,
    int failedCount,
    String errorDetail,
    Instant nextDispatchAt,
    Instant waPausedUntil,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    UUID campaignId
) {
}
