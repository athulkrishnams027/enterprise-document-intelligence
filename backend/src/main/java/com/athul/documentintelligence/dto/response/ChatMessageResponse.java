package com.athul.documentintelligence.dto.response;

import com.athul.documentintelligence.enums.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        List<SourceCitationResponse> citations,
        Long processingTimeMs,
        TokenUsageResponse tokenUsage,
        Instant createdAt
) {
}
