package com.prospectportal.module.ingestion.dto;

import java.time.Instant;
import java.util.UUID;

public record ImportJobResponse(
    UUID id,
    String jobType,
    String status,
    long processedRows,
    long insertedRows,
    long skippedRows,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt
) {
}
