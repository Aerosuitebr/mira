package com.prospectportal.module.ingestion.dto;

import java.util.List;

public record RfImportRequest(
    List<String> states,
    boolean loadEmpresas,
    boolean geocodeAfterImport,
    boolean syncElasticsearch,
    List<String> estabelecimentoFiles
) {
    public RfImportRequest {
        if (states == null || states.isEmpty()) {
            states = List.of("RJ");
        }
        if (estabelecimentoFiles == null) {
            estabelecimentoFiles = List.of();
        }
    }
}
