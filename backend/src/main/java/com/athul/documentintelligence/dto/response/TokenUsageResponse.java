package com.athul.documentintelligence.dto.response;

public record TokenUsageResponse(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
