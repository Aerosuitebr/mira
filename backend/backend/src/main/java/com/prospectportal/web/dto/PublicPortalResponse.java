package com.prospectportal.web.dto;

import java.util.List;

public record PublicPortalResponse(
    String clientName,
    String projectName,
    String status,
    int progressPercent,
    List<PublicTimelineItem> timeline,
    List<String> deliverables
) {
}
