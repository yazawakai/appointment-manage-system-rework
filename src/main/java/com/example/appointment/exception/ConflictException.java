package com.example.appointment.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 409 Conflict. */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
