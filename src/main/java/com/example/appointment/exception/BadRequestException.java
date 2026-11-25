package com.example.appointment.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 400 Bad Request. */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
