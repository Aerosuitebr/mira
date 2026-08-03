package com.prospectportal.web.dto;

import java.util.List;

public record CnaeSectionCatalogResponse(
    String sectionCode,
    String title,
    String searchHint,
    String sectionFilterValue,
    List<CnaeActivityOptionResponse> activities
) {
}
