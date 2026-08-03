package com.prospectportal.web.dto;

import java.util.UUID;

public record ContactResponse(
    UUID id,
    String fullName,
    String roleTitle,
    String email,
    String phone,
    String whatsapp,
    String linkedinUrl,
    String websiteUrl,
    String instagramUrl,
    short confidence,
    String source
) {
}
