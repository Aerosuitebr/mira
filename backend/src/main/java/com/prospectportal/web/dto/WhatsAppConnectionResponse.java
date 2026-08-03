package com.prospectportal.web.dto;

public record WhatsAppConnectionResponse(
    boolean providerEnabled,
    boolean connected,
    String status,
    String statusLabel,
    String instanceName,
    String phone,
    String qrCodeBase64,
    String hint,
    String connectedAt
) {
}
