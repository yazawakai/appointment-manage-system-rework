package com.example.appointment.dto;

import java.time.LocalDateTime;

public record ProviderResponse(
        Long id,
        String name,
        String email,
        String phone,
        String serviceCategory,
        LocalDateTime createdAt
) {
}
