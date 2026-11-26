package com.athul.documentintelligence.dto.response;

import com.athul.documentintelligence.enums.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentStatusResponse(
        UUID id,
        DocumentStatus status,
        String processingError,
        Integer totalPages,
        int totalChunks,
        Instant lastProcessedAt
) {
}
