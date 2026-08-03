package com.prospectportal.web.dto;

public record WhatsAppSendRequest(
    String phone,
    String message,
    String clientId
) {
}
