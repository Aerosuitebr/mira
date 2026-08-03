package com.prospectportal.module.ingestion.dto;

import java.util.List;

public record ReindexRequest(
    List<String> states,
    boolean recreateIndex
) {
    public ReindexRequest {
        if (states == null || states.isEmpty()) {
            states = List.of("RJ", "SP", "MG", "ES", "DF", "GO", "MT", "MS");
        }
    }
}
