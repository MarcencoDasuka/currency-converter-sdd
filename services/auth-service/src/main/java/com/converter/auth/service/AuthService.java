package com.converter.auth.service;

import com.converter.auth.dto.AuthDtos.AuthTokenResponse;
import com.converter.auth.dto.AuthDtos.LoginRequest;
import com.converter.auth.dto.AuthDtos.RegisterRequest;
import com.converter.auth.dto.AuthDtos.UserProfileResponse;
import com.converter.auth.entity.UserEntity;
import com.converter.auth.repository.UserRepository;
import com.converter.common.exception.PlatformExceptions.ConflictException;
import com.converter.common.exception.PlatformExceptions.ForbiddenException;
import com.converter.common.exception.PlatformExceptions.ResourceNotFoundException;
import com.converter.common.exception.PlatformExceptions.UnauthorizedException;
import com.converter.common.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-constant-time-guard");
    }

    public AuthTokenResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        String hash = passwordEncoder.encode(request.password());
        Instant now = clock.instant();
        UserEntity entity = UserEntity.createStandardUser(
                UUID.randomUUID(),
                normalizedEmail,
                request.fullName().trim(),
                hash,
                now
        );

        UserEntity saved;
        try {
            saved = transactionTemplate.execute(status -> userRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email already exists");
        }
        if (saved == null) {
            throw new IllegalStateException("Failed to persist registered user");
        }

        String token = jwtTokenService.issueAccessToken(saved.getId(), saved.getEmail(), saved.getRole());
        return new AuthTokenResponse(token, "Bearer", jwtTokenService.getAccessTokenTtlSeconds(), toProfile(saved));
    }

    public AuthTokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("Account is deactivated");
        }

        String token = jwtTokenService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthTokenResponse(token, "Bearer", jwtTokenService.getAccessTokenTtlSeconds(), toProfile(user));
    }

    public UserProfileResponse getProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("user-not-found", "User Not Found", "User account not found"));
        return toProfile(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserProfileResponse toProfile(UserEntity user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
