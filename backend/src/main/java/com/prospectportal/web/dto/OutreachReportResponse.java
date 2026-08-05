package com.prospectportal.web.dto;

public record OutreachReportResponse(
    long firstStepSent,
    long repliesReceived,
    long followUpsAwaitingApproval,
    long followUpsSent,
    long followUpsFailed
) {
}
