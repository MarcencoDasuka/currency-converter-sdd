package com.converter.common.security;

import com.converter.common.exception.PlatformExceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-length-1234";

    @Test
    void constructor_RejectsShortSecret() {
        JwtProperties shortProps = new JwtProperties("too-short", 3600, "issuer");
        assertThatThrownBy(() -> new JwtTokenService(shortProps, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void issueAndVerify_ValidToken_ReturnsAuthenticatedUser() {
        Instant fixed = Instant.parse("2026-10-01T10:00:00Z");
        JwtTokenService service = new JwtTokenService(
                new JwtProperties(SECRET, 3600, "currency-platform-auth"),
                Clock.fixed(fixed, ZoneOffset.UTC)
        );
        UUID userId = UUID.randomUUID();

        String token = service.issueAccessToken(userId, "alice@example.com", "USER");
        AuthenticatedUser user = service.verify(token);

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.role()).isEqualTo("USER");
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void verify_ExpiredToken_ThrowsUnauthorized() {
        Instant issuedAt = Instant.parse("2026-10-01T10:00:00Z");
        JwtProperties props = new JwtProperties(SECRET, 60, "currency-platform-auth");
        JwtTokenService issuer = new JwtTokenService(props, Clock.fixed(issuedAt, ZoneOffset.UTC));
        JwtTokenService verifier = new JwtTokenService(props, Clock.fixed(issuedAt.plusSeconds(61), ZoneOffset.UTC));

        String token = issuer.issueAccessToken(UUID.randomUUID(), "alice@example.com", "USER");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_TamperedSignature_ThrowsUnauthorized() {
        JwtTokenService service = new JwtTokenService(
                new JwtProperties(SECRET, 3600, "currency-platform-auth"),
                Clock.systemUTC()
        );
        String token = service.issueAccessToken(UUID.randomUUID(), "alice@example.com", "USER");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_UnsignedNoneAlgorithmToken_ThrowsUnauthorized() {
        JwtTokenService service = new JwtTokenService(
                new JwtProperties(SECRET, 3600, "currency-platform-auth"),
                Clock.systemUTC()
        );
        String noneToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0NTY3OC0xMjM0LTEyMzQtMTIzNC0xMjM0NTY3ODkwMTIiLCJyb2xlIjoiQURNSU4ifQ.";

        assertThatThrownBy(() -> service.verify(noneToken))
                .isInstanceOf(UnauthorizedException.class);
    }
}
