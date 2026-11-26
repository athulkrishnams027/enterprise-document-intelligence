package com.athul.documentintelligence.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserProfileResponse user
) {
}
