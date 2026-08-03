package com.prospectportal.web.dto;

import java.util.UUID;

public record TemplateResponse(
    UUID id,
    String name,
    String channel,
    String subject,
    String bodyTemplate
) {
}
