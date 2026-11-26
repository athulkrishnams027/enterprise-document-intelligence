package com.athul.documentintelligence.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        Set<String> roles,
        boolean enabled,
        Instant createdAt
) {
}
