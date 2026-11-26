package com.athul.documentintelligence.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DocumentProcessingLogResponse(
        UUID id,
        String stage,
        String status,
        String message,
        Long durationMs,
        Instant createdAt
) {
}
