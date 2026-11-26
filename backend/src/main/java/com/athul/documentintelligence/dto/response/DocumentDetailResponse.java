package com.athul.documentintelligence.dto.response;

import com.athul.documentintelligence.enums.DocumentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID id,
        UUID ownerId,
        String originalFilename,
        String mimeType,
        long fileSize,
        String fileChecksum,
        DocumentStatus status,
        String processingError,
        Integer totalPages,
        int totalChunks,
        String extractionSummary,
        Instant uploadedAt,
        Instant lastProcessedAt,
        Instant updatedAt,
        List<DocumentProcessingLogResponse> processingLogs
) {
}
