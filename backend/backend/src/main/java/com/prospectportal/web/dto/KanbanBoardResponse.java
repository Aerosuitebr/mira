package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record KanbanBoardResponse(
    UUID pipelineId,
    String pipelineName,
    List<KanbanStageResponse> stages
) {
}
