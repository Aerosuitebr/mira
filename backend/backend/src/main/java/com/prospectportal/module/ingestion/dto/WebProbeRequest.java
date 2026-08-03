package com.prospectportal.module.ingestion.dto;

import java.util.List;

public record WebProbeRequest(
    List<String> states,
    int limit
) {
    public WebProbeRequest {
        if (states == null || states.isEmpty()) {
            states = List.of("RJ", "SP", "MG", "ES", "DF", "GO", "MT", "MS");
        }
        if (limit <= 0) {
            limit = 500;
        }
    }
}
