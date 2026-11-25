package com.example.appointment.exception;

/**
 * Central registry of machine-readable error codes returned in the {@code code}
 * field of the unified error body.
 */
public final class ErrorCode {

    // 400
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String INVALID_TIME_RANGE = "INVALID_TIME_RANGE";
    public static final String INVALID_DATE = "INVALID_DATE";

    // 403
    public static final String CUSTOMER_NOT_OWNER = "CUSTOMER_NOT_OWNER";

    // 404
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String PROVIDER_NOT_FOUND = "PROVIDER_NOT_FOUND";
    public static final String CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";
    public static final String SLOT_NOT_FOUND = "SLOT_NOT_FOUND";
    public static final String APPOINTMENT_NOT_FOUND = "APPOINTMENT_NOT_FOUND";

    // 409
    public static final String SLOT_OVERLAP = "SLOT_OVERLAP";
    public static final String SLOT_ALREADY_BOOKED = "SLOT_ALREADY_BOOKED";
    public static final String SLOT_EXPIRED = "SLOT_EXPIRED";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String APPOINTMENT_ALREADY_CANCELED = "APPOINTMENT_ALREADY_CANCELED";
    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String PROVIDER_HAS_SLOTS = "PROVIDER_HAS_SLOTS";
    public static final String DATA_INTEGRITY_VIOLATION = "DATA_INTEGRITY_VIOLATION";

    // 500
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCode() {
    }
}
