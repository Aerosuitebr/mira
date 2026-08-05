package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record FollowUpReviewItem(UUID id, UUID companyId, String companyName, String recipient, String body, Instant createdAt) {
}
