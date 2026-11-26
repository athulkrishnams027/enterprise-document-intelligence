package com.athul.documentintelligence.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateConversationRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,
        UUID documentId
) {
}
