package com.prospectportal.web.dto;

import java.util.UUID;

public record MoveCardRequest(UUID stageId, int position) {
}
