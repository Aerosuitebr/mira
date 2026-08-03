package com.prospectportal.web.dto;

public record ProspectJobRequest(
    String name,
    String cnae,
    String state,
    String city,
    String keyword,
    Integer companyLimit,
    Boolean testMode,
    Boolean dryRun
) {
}
