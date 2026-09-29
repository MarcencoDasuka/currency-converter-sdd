package com.converter.exception;

public class BulletinUnavailableException extends RuntimeException {
    public BulletinUnavailableException(String message) {
        super(message);
    }

    public BulletinUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
