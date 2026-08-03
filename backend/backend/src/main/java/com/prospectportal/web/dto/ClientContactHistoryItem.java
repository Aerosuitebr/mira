package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientContactHistoryItem(
    UUID id,
    String channel,
    String subject,
    String status,
    Instant sentAt,
    Instant repliedAt
) {
}
