package com.prospectportal.web.dto;

public record OutreachSettingsResponse(
    String senderName,
    boolean hasBrandImage,
    String brandImageMime,
    String brandImageFileName,
    String brandImageBase64
) {
}
