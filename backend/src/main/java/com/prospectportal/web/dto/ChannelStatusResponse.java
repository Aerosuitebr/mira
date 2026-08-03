package com.prospectportal.web.dto;

public record ChannelStatusResponse(
    boolean emailConfigured,
    boolean emailTestMode,
    String emailTestAddress,
    String emailLabel,
    boolean whatsappEnabled,
    boolean whatsappConnected,
    String whatsappLabel,
    String whatsappInstance,
    String whatsappRawState
) {
}
