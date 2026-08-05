package com.prospectportal.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BulkOutreachRequest(
    String campaignName,
    UUID templateId,
    String channel,
    List<UUID> companyIds,
    String productDescription,
    Map<String, MessageOverride> messages,
    Boolean emailFallback,
    String approachId,
    String editableBody,
    String editableSubject
) {
    public record MessageOverride(String subject, String body) {
    }

    public boolean emailFallbackEnabled() {
        return emailFallback == null || emailFallback;
    }
}
