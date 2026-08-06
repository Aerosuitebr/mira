package com.prospectportal.web.dto;

import java.util.UUID;

public record AuthResponse(
    String token,
    UUID userId,
    UUID tenantId,
    String fullName,
    String email,
    String role,
    String planCode,
    int creditsRemaining,
    int monthlyCredits
) {
}
