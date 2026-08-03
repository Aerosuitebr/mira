package com.prospectportal.web.dto;

public record CnaeActivityOptionResponse(
    String code,
    String label,
    String filterValue,
    String kind
) {
}
