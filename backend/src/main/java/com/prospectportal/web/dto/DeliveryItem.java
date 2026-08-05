package com.prospectportal.web.dto;

import java.util.UUID;

public record DeliveryItem(
    UUID companyId,
    String companyName,
    String status,
    String channel,
    String provider,
    String recipient,
    boolean fallback,
    String detail
) {
}
