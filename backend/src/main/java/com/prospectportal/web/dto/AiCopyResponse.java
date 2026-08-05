package com.prospectportal.web.dto;

import java.util.List;

public record AiCopyResponse(
    String subject,
    String body,
    String channel,
    String greeting,
    String editableBody,
    String selectedApproachId,
    List<ApproachVariant> approaches
) {
    public record ApproachVariant(
        String id,
        String label,
        String description,
        String greeting,
        String body,
        String subject
    ) {
    }
}
