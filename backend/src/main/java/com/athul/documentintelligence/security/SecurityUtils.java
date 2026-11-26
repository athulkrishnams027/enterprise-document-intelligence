package com.athul.documentintelligence.security;

import com.athul.documentintelligence.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Derives the authenticated user identity strictly from Spring SecurityContextHolder.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthenticatedUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("Authentication is required");
        }
        return user;
    }

    public static UUID requireCurrentUserId() {
        return requireCurrentUser().getId();
    }
}
