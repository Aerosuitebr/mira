package com.prospectportal.web.dto;

import java.util.UUID;

public record ProposalRecipientOption(
    String key,
    String label,
    String subtitle,
    String phone,
    String source
) {
    public static String leadKey(UUID id) {
        return "LEAD:" + id;
    }

    public static String clientKey(UUID id) {
        return "CLIENT:" + id;
    }

    public static String companyKey(UUID id) {
        return "COMPANY:" + id;
    }
}
