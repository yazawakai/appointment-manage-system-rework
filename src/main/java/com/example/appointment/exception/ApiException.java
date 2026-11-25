package com.example.appointment.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain exceptions that map to a specific HTTP status and
 * machine-readable error code.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
