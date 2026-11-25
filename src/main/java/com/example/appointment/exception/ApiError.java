package com.example.appointment.exception;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The unified error body returned for every API failure.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<Violation> details
) {

    public record Violation(String field, String message) {
    }

    public static ApiError of(HttpStatus status, String code, String message, String path, List<Violation> details) {
        return new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                path,
                details == null ? List.of() : details
        );
    }
}
