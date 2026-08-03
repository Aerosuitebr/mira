package com.prospectportal.module.ingestion.dto;

import java.util.List;

public record RfEnrichRequest(
    List<String> states,
    boolean geocode,
    boolean syncElasticsearch
) {
    public RfEnrichRequest {
        if (states == null || states.isEmpty()) {
            states = List.of("RJ");
        }
    }
}
