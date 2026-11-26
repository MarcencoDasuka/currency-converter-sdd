package com.converter.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email is too long")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
            String password,

            @NotBlank(message = "Full name is required")
            @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
            String fullName
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record UserProfileResponse(
            UUID id,
            String email,
            String fullName,
            String role,
            boolean active,
            Instant createdAt
    ) {}

    public record AuthTokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserProfileResponse user
    ) {}
}
