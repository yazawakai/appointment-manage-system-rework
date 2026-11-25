package com.example.appointment.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 404 Not Found. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
