package com.converter.gateway.controller;

import com.converter.common.exception.ApiException;
import com.converter.common.exception.PlatformExceptions.BadGatewayException;
import com.converter.common.exception.PlatformExceptions.BadRequestException;
import com.converter.common.exception.PlatformExceptions.ResourceNotFoundException;
import com.converter.common.exception.PlatformExceptions.TooManyRequestsException;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.common.web.RequestIdFilter;
import com.converter.gateway.config.GatewayProperties;
import com.converter.gateway.ratelimit.SlidingWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Set;

@RestController
public class GatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyController.class);
    private static final Set<String> SAFE_RESPONSE_HEADERS = Set.of(
            "content-type",
            "x-request-id",
            "x-conversion-id",
            "retry-after",
            "cache-control"
    );

    private final RestClient proxyRestClient;
    private final GatewayProperties properties;
    private final SlidingWindowRateLimiter rateLimiter;

    public GatewayProxyController(
            RestClient proxyRestClient,
            GatewayProperties properties,
            SlidingWindowRateLimiter rateLimiter
    ) {
        this.proxyRestClient = proxyRestClient;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @RequestMapping("/api/v1/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String rawUri = request.getRequestURI();
        validateRawPath(rawUri);

        String normalizedPath = normalizePath(rawUri);
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        // Fail-closed authentication check
        final AuthenticatedUser user = isPublicRoute(method, normalizedPath)
                ? AuthContext.optionalUser(request).orElse(null)
                : AuthContext.requireUser(request);

        // Distributed sliding-window rate limiting
        boolean isAuthRoute = "/api/v1/auth/login".equals(normalizedPath)
                || "/api/v1/auth/register".equals(normalizedPath);
        String bucket = isAuthRoute ? "auth" : "api";
        int limit = isAuthRoute
                ? properties.rateLimit().authRequestsPerWindow()
                : properties.rateLimit().defaultRequestsPerWindow();
        String clientKey = resolveClientKey(request, user);
        SlidingWindowRateLimiter.RateLimitDecision decision = rateLimiter.check(bucket, clientKey, limit);
        if (!decision.allowed()) {
            throw new TooManyRequestsException("Rate limit exceeded. Please retry later.", decision.retryAfterSeconds());
        }

        String targetBaseUrl = resolveUpstreamBaseUrl(normalizedPath);
        if (targetBaseUrl == null) {
            throw new ResourceNotFoundException("No route registered for " + normalizedPath);
        }

        String query = request.getQueryString();
        URI targetUri = URI.create(targetBaseUrl + normalizedPath + (query != null && !query.isBlank() ? "?" + query : ""));
        byte[] body = readBoundedBody(request);
        String requestId = RequestIdFilter.currentRequestId(request);

        try {
            return proxyRestClient.method(HttpMethod.valueOf(method))
                    .uri(targetUri)
                    .headers(headers -> populateUpstreamHeaders(headers, request, user, requestId))
                    .body(body)
                    .exchange((req, res) -> {
                        byte[] respBytes = res.getBody().readAllBytes();
                        HttpHeaders respHeaders = new HttpHeaders();
                        res.getHeaders().forEach((name, values) -> {
                            if (SAFE_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                                respHeaders.put(name, values);
                            }
                        });
                        if (requestId != null && !respHeaders.containsKey(RequestIdFilter.HEADER_NAME)) {
                            respHeaders.set(RequestIdFilter.HEADER_NAME, requestId);
                        }
                        if (!respHeaders.containsKey(HttpHeaders.CONTENT_TYPE) && respBytes.length > 0) {
                            respHeaders.setContentType(MediaType.APPLICATION_JSON);
                        }
                        return ResponseEntity.status(res.getStatusCode()).headers(respHeaders).body(respBytes);
                    });
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            if (hasTimeoutCause(ex)) {
                log.warn("Upstream timeout calling {}: {}", targetUri, ex.getMessage());
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "gateway-timeout", "Gateway Timeout",
                        "Downstream microservice did not respond in time");
            }
            log.error("Upstream communication failure calling {}: {}", targetUri, ex.getMessage());
            throw new BadGatewayException("Downstream microservice is temporarily unreachable", ex);
        }
    }

    private void populateUpstreamHeaders(
            HttpHeaders out,
            HttpServletRequest in,
            AuthenticatedUser user,
            String requestId
    ) {
        String contentType = in.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            out.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        String accept = in.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && !accept.isBlank()) {
            out.set(HttpHeaders.ACCEPT, accept);
        }
        if (requestId != null && !requestId.isBlank()) {
            out.set(RequestIdFilter.HEADER_NAME, requestId);
        }
        // Only forward verified Authorization & inject verified identity headers (never trust incoming X-User-*)
        if (user != null) {
            String auth = in.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && !auth.isBlank()) {
                out.set(HttpHeaders.AUTHORIZATION, auth);
            }
            out.set("X-User-Id", user.userId().toString());
            out.set("X-User-Role", user.role());
        }
    }

    private static void validateRawPath(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            throw new BadRequestException("Invalid request path");
        }
        String candidate = rawUri;
        for (int i = 0; i < 4; i++) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.contains("..")
                    || lower.contains("%2e")
                    || lower.contains("%25")
                    || lower.contains("\\")
                    || lower.contains("%5c")
                    || lower.contains("\u0000")
                    || lower.contains("%00")
                    || lower.contains("//")) {
                throw new BadRequestException("Path traversal or malformed path sequence is not allowed");
            }
            try {
                String decoded = java.net.URLDecoder.decode(candidate, java.nio.charset.StandardCharsets.UTF_8);
                if (decoded.equals(candidate)) {
                    break;
                }
                candidate = decoded;
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Malformed percent-encoding in request path");
            }
        }
    }

    private static String normalizePath(String rawUri) {
        if (rawUri.length() > 1 && rawUri.endsWith("/")) {
            return rawUri.substring(0, rawUri.length() - 1);
        }
        return rawUri;
    }

    private static boolean isPublicRoute(String method, String path) {
        if ("OPTIONS".equals(method)) {
            return true;
        }
        if ("POST".equals(method) && ("/api/v1/auth/register".equals(path) || "/api/v1/auth/login".equals(path))) {
            return true;
        }
        if ("GET".equals(method) && ("/api/v1/currencies".equals(path) || "/api/v1/rates".equals(path))) {
            return true;
        }
        // Conversion remains publicly usable for anonymous Vue visitors; if Bearer JWT is provided, JwtAuthFilter validates it
        return "POST".equals(method) && "/api/v1/convert".equals(path);
    }

    private String resolveUpstreamBaseUrl(String path) {
        GatewayProperties.Upstreams u = properties.upstreams();
        if ("/api/v1/auth".equals(path) || path.startsWith("/api/v1/auth/")) {
            return u.authServiceUrl();
        }
        if ("/api/v1/currencies".equals(path) || path.startsWith("/api/v1/currencies/")
                || "/api/v1/rates".equals(path) || path.startsWith("/api/v1/rates/")) {
            return u.rateServiceUrl();
        }
        if ("/api/v1/convert".equals(path)
                || "/api/v1/conversions".equals(path) || path.startsWith("/api/v1/conversions/")) {
            return u.conversionServiceUrl();
        }
        if ("/api/v1/alerts".equals(path) || path.startsWith("/api/v1/alerts/")) {
            return u.alertServiceUrl();
        }
        if ("/api/v1/notifications".equals(path) || path.startsWith("/api/v1/notifications/")) {
            return u.notificationServiceUrl();
        }
        return null;
    }

    private String resolveClientKey(HttpServletRequest request, AuthenticatedUser user) {
        if (user != null) {
            return "user:" + user.userId();
        }
        if (properties.trustProxyHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded.split(",")[0].trim();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        long max = properties.maxBodyBytes();
        if (request.getContentLengthLong() > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload Too Large",
                    "Request payload exceeds maximum allowed size");
        }
        try (InputStream in = request.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > max) {
                    throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload Too Large",
                            "Request payload exceeds maximum allowed size");
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static boolean hasTimeoutCause(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException || cur instanceof HttpTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
