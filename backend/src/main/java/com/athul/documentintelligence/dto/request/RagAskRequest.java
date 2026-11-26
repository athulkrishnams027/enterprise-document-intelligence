package com.athul.documentintelligence.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RagAskRequest(
        UUID conversationId,
        UUID documentId,

        @NotBlank(message = "Question is required")
        @Size(max = 4000, message = "Question must not exceed 4000 characters")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must not exceed 20")
        Integer topK,

        @DecimalMin(value = "0.0", message = "similarityThreshold must be >= 0.0")
        @DecimalMax(value = "1.0", message = "similarityThreshold must be <= 1.0")
        Double similarityThreshold,

        @DecimalMin(value = "0.0", message = "temperature must be >= 0.0")
        @DecimalMax(value = "1.0", message = "temperature must be <= 1.0")
        Double temperature
) {
}
