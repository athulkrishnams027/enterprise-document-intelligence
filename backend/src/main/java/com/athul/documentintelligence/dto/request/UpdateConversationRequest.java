package com.athul.documentintelligence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @NotBlank(message = "Conversation title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title
) {
}
