package com.converter.common.security;

import com.converter.common.exception.PlatformExceptions.UnauthorizedException;
import com.converter.common.web.ProblemDetailsDto;
import com.converter.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Verifies Authorization Bearer JWT tokens on every request (defense-in-depth).
 * Never trusts spoofable X-User-Id / X-User-Role headers from callers.
 * If an Authorization header is present and invalid, immediately responds with 401 RFC 9457.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && !authHeader.isBlank()) {
            if (!authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
                writeUnauthorized(request, response, "Authorization header must use Bearer token scheme");
                return;
            }
            String token = authHeader.substring(7).trim();
            try {
                AuthenticatedUser user = jwtTokenService.verify(token);
                request.setAttribute(AuthenticatedUser.REQUEST_ATTR, user);
                MDC.put("user_id", user.userId().toString());
            } catch (UnauthorizedException ex) {
                writeUnauthorized(request, response, ex.getMessage());
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("user_id");
        }
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String detail)
            throws IOException {
        String requestId = RequestIdFilter.currentRequestId(request);
        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/unauthorized"),
                "Unauthorized",
                HttpStatus.UNAUTHORIZED.value(),
                detail,
                request.getRequestURI(),
                requestId != null ? Map.of("requestId", requestId) : Map.of(),
                List.of()
        );
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
