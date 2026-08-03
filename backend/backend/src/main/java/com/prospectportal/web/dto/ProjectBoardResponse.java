package com.prospectportal.web.dto;

import java.util.List;

public record ProjectBoardResponse(
    List<ProjectColumnResponse> columns
) {
}
