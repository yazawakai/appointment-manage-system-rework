package com.example.appointment.dto;

import com.example.appointment.entity.AppointmentStatus;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        Long customerId,
        Long slotId,
        Long providerId,
        AppointmentStatus status,
        LocalDateTime bookedAt,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        String cancelReason
) {
}
