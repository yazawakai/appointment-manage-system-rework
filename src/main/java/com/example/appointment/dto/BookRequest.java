package com.example.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookRequest(
        @NotNull(message = "customerId must not be null")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @NotNull(message = "slotId must not be null")
        @Positive(message = "slotId must be positive")
        Long slotId
) {
}
