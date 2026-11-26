package com.converter.common.security;

import com.converter.common.exception.PlatformExceptions.UnauthorizedException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class JwtTokenService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final byte[] secretBytes;
    private final long accessTokenTtlSeconds;
    private final String issuer;
    private final Clock clock;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        if (properties == null || properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("JWT secret (platform.jwt.secret / JWT_SECRET) must be configured");
        }
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.secretBytes = bytes;
        this.accessTokenTtlSeconds = properties.accessTokenTtlSeconds();
        this.issuer = properties.issuer();
        this.clock = clock;
        try {
            this.signer = new MACSigner(this.secretBytes);
            this.verifier = new MACVerifier(this.secretBytes);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to initialize JWT signer/verifier", ex);
        }
    }

    public String issueAccessToken(UUID userId, String email, String role) {
        if (userId == null || email == null || email.isBlank() || role == null || !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid JWT subject or role");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign JWT token", ex);
        }
    }

    public AuthenticatedUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing authentication token");
        }
        try {
            SignedJWT signedJWT = SignedJWT.parse(token.trim());
            JWSAlgorithm alg = signedJWT.getHeader().getAlgorithm();
            if (!JWSAlgorithm.HS256.equals(alg)) {
                throw new UnauthorizedException("Unsupported JWT signing algorithm");
            }
            if (!signedJWT.verify(verifier)) {
                throw new UnauthorizedException("Invalid JWT signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Instant now = clock.instant();
            Date exp = claims.getExpirationTime();
            if (exp == null || !exp.toInstant().isAfter(now)) {
                throw new UnauthorizedException("Authentication token has expired");
            }
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && nbf.toInstant().isAfter(now.plusSeconds(5))) {
                throw new UnauthorizedException("Authentication token is not yet valid");
            }
            if (issuer != null && !issuer.isBlank() && !issuer.equals(claims.getIssuer())) {
                throw new UnauthorizedException("Invalid authentication token issuer");
            }

            String sub = claims.getSubject();
            String email = claims.getStringClaim("email");
            String role = claims.getStringClaim("role");
            if (sub == null || role == null || !ALLOWED_ROLES.contains(role)) {
                throw new UnauthorizedException("Invalid authentication token claims");
            }

            UUID userId = UUID.fromString(sub);
            return new AuthenticatedUser(userId, email != null ? email : "", role);
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Malformed or invalid authentication token");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
