package com.converter.common.exception;

import org.springframework.http.HttpStatus;

public final class PlatformExceptions {

    private PlatformExceptions() {}

    public static class BadRequestException extends ApiException {
        public BadRequestException(String detail) {
            super(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", detail);
        }
    }

    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String detail) {
            super(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", detail);
        }
    }

    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String detail) {
            super(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", detail);
        }
    }

    public static class ResourceNotFoundException extends ApiException {
        public ResourceNotFoundException(String typeSlug, String title, String detail) {
            super(HttpStatus.NOT_FOUND, typeSlug, title, detail);
        }

        public ResourceNotFoundException(String detail) {
            super(HttpStatus.NOT_FOUND, "not-found", "Resource Not Found", detail);
        }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String detail) {
            super(HttpStatus.CONFLICT, "conflict", "Conflict", detail);
        }
    }

    public static class TooManyRequestsException extends ApiException {
        private final long retryAfterSeconds;

        public TooManyRequestsException(String detail, long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", "Too Many Requests", detail);
            this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class BadGatewayException extends ApiException {
        public BadGatewayException(String detail) {
            super(HttpStatus.BAD_GATEWAY, "bad-gateway", "Bad Gateway", detail);
        }

        public BadGatewayException(String detail, Throwable cause) {
            super(HttpStatus.BAD_GATEWAY, "bad-gateway", "Bad Gateway", detail, cause);
        }
    }

    public static class ServiceUnavailableException extends ApiException {
        public ServiceUnavailableException(String typeSlug, String title, String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE, typeSlug, title, detail);
        }

        public ServiceUnavailableException(String detail) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Service Unavailable", detail);
        }
    }
}
