package com.example.appointment.entity;

/**
 * Lifecycle states of an appointment.
 */
public enum AppointmentStatus {
    /** Created; the slot is reserved. */
    BOOKED,
    /** The provider has accepted the booking. */
    CONFIRMED,
    /** The service has been delivered. */
    COMPLETED,
    /** Cancelled; the slot has been released back to AVAILABLE. */
    CANCELED
}
