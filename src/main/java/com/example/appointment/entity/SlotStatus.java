package com.example.appointment.entity;

/**
 * Lifecycle states of a provider's availability slot.
 */
public enum SlotStatus {
    /** Bookable by a customer. */
    AVAILABLE,
    /** Temporarily reserved during a booking attempt. */
    LOCKED,
    /** Attached to a live appointment. */
    BOOKED,
    /** End time is already in the past. */
    EXPIRED
}
