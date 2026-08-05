package com.prospectportal.web.dto;

import java.util.UUID;

public record FollowUpApprovalResponse(UUID id, String status, String error) {
}
