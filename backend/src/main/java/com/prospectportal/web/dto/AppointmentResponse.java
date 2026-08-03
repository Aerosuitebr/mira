package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID clientId,
    String clientName,
    String clientEmail,
    String clientPhone,
    String clientCompany,
    String title,
    String description,
    String location,
    boolean videoConference,
    String meetingUrl,
    Instant startsAt,
    Instant endsAt,
    int reminderMinutesBefore,
    boolean reminderSent,
    String status,
    String ownerName,
    Instant createdAt
) {
}
