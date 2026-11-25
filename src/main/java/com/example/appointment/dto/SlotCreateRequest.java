package com.example.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotCreateRequest(
        @NotNull(message = "providerId must not be null")
        @Positive(message = "providerId must be positive")
        Long providerId,

        @NotNull(message = "date must not be null")
        LocalDate date,

        @NotNull(message = "startTime must not be null")
        LocalTime startTime,

        @NotNull(message = "endTime must not be null")
        LocalTime endTime
) {
}
