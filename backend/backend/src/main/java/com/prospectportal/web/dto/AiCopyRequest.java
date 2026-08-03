package com.prospectportal.web.dto;

import java.util.UUID;

public record AiCopyRequest(
    UUID companyId,
    String channel,
    String productDescription,
    String tone
) {
}
