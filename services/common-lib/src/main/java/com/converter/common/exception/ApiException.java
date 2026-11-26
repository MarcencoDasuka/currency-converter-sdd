package com.converter.common.exception;

import org.springframework.http.HttpStatus;

import java.net.URI;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;

    public ApiException(HttpStatus status, String typeSlug, String title, String detail) {
        super(detail);
        this.status = status;
        this.type = URI.create("https://api.currency-converter.local/errors/" + typeSlug);
        this.title = title;
    }

    public ApiException(HttpStatus status, String typeSlug, String title, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.type = URI.create("https://api.currency-converter.local/errors/" + typeSlug);
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public URI getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}
