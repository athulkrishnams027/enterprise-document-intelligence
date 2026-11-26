package com.athul.documentintelligence.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AiAuditLogResponse(
        UUID id,
        UUID userId,
        UUID conversationId,
        UUID documentId,
        String operation,
        String provider,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long durationMs,
        String status,
        String errorMessage,
        Instant createdAt
) {
}
