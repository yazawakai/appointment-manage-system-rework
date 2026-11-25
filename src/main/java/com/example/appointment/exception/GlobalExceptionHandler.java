package com.example.appointment.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Single point of truth for converting exceptions into the unified error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), null);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.Violation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.Violation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.Violation> details = ex.getConstraintViolations().stream()
                .map(cv -> new ApiError.Violation(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Malformed request body or unparseable value",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Invalid value for query parameter '" + ex.getName() + "'",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT,
                ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified concurrently; please retry",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        ApiError body = ApiError.of(
                HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with existing data",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "No resource found at this path",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        ApiError body = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
