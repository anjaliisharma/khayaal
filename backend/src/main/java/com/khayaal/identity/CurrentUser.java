package com.khayaal.identity;

import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() { }

    public static AuthenticatedUser require(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        return user;
    }
}
