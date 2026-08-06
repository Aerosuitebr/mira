package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignMessageDetail(
    UUID id, UUID companyId, String companyName, String cnpj, String city, String state,
    String contactName, String contactRole, String recipient, String email,
    short step, String channel, String status, String subject, String body,
    Instant createdAt, Instant sentAt, Instant repliedAt, Instant approvedAt,
    String providerMessageId, String errorDetail, boolean editable, boolean retryable
) {}
