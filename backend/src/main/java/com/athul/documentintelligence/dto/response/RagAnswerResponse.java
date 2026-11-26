package com.athul.documentintelligence.dto.response;

import java.util.List;
import java.util.UUID;

public record RagAnswerResponse(
        UUID conversationId,
        UUID messageId,
        String answer,
        List<SourceCitationResponse> sources,
        long processingTimeMs,
        TokenUsageResponse tokenUsage,
        boolean grounded
) {
}
