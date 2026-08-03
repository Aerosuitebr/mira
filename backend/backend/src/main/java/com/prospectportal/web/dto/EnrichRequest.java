package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record EnrichRequest(List<UUID> companyIds, Boolean forceRefresh) {
    public EnrichRequest {
        if (companyIds == null) {
            companyIds = List.of();
        }
    }

    public boolean isForceRefresh() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}
