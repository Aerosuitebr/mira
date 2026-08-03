package com.prospectportal.web.dto;

import java.util.List;
import java.util.UUID;

public record PageResponse<T>(
    List<T> content,
    long totalElements,
    int totalPages,
    int page,
    int size
) {
}
