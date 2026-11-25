package com.example.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @NotNull(message = "customerId must not be null")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @NotBlank(message = "reason must not be blank")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason
) {
}
