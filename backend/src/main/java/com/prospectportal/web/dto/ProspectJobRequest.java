package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record ProspectJobRequest(
    String name,
    String cnae,
    String state,
    String city,
    String keyword,
    Integer companyLimit,
    Boolean testMode,
    Boolean dryRun,
    List<UUID> selectedCompanyIds,
    String openingMessage,
    String followUpBody
) {
}
