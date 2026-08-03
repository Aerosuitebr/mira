package com.prospectportal.web.dto;

public record OutreachSettingsRequest(
    String senderName,
    String brandImageBase64,
    String brandImageMime,
    String brandImageFileName,
    Boolean clearBrandImage
) {
}
