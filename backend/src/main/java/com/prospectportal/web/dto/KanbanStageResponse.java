package com.prospectportal.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record KanbanStageResponse(
    UUID id,
    String name,
    int position,
    String color,
    List<KanbanCardResponse> cards
) {
}
