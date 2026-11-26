package com.converter.common.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        String role
) {
    public static final String REQUEST_ATTR = "platform.authenticatedUser";

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
