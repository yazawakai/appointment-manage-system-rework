package com.example.appointment.dto;

import com.example.appointment.entity.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(
        Long id,
        Long providerId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status
) {
}
