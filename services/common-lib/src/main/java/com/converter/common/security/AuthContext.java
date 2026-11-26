package com.converter.common.security;

import com.converter.common.exception.PlatformExceptions.ForbiddenException;
import com.converter.common.exception.PlatformExceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.UUID;

public final class AuthContext {

    private AuthContext() {}

    public static Optional<AuthenticatedUser> optionalUser(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthenticatedUser.REQUEST_ATTR);
        if (attr instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser requireUser(HttpServletRequest request) {
        return optionalUser(request)
                .orElseThrow(() -> new UnauthorizedException("Valid Bearer token is required"));
    }

    public static void requireOwnerOrAdmin(AuthenticatedUser user, UUID ownerId) {
        if (user == null) {
            throw new UnauthorizedException("Valid Bearer token is required");
        }
        if (user.isAdmin()) {
            return;
        }
        if (ownerId == null || !ownerId.equals(user.userId())) {
            throw new ForbiddenException("Access denied to this resource");
        }
    }
}
