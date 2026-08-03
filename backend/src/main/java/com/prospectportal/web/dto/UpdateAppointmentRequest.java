package com.prospectportal.web.dto;

import java.time.Instant;
import java.util.UUID;

public record UpdateAppointmentRequest(
    UUID clientId,
    String clientName,
    String clientEmail,
    String clientPhone,
    String clientCompany,
    String title,
    String description,
    String location,
    Boolean videoConference,
    String meetingUrl,
    Instant startsAt,
    Instant endsAt,
    Integer reminderMinutesBefore,
    String status
) {
}
