package com.prospectportal.web.dto;

public record WhatsAppSendResponse(
    boolean success,
    String phone,
    String messageId,
    String error
) {
}
