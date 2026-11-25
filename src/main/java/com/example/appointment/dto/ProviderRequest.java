package com.example.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 30, message = "phone must be at most 30 characters")
        String phone,

        @NotBlank(message = "serviceCategory must not be blank")
        @Size(max = 50, message = "serviceCategory must be at most 50 characters")
        String serviceCategory
) {
}
