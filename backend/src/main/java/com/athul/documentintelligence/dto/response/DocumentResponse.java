package com.athul.documentintelligence.dto.response;

import com.athul.documentintelligence.enums.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String originalFilename,
        String mimeType,
        long fileSize,
        DocumentStatus status,
        String processingError,
        Integer totalPages,
        int totalChunks,
        Instant uploadedAt,
        Instant lastProcessedAt
) {
}
