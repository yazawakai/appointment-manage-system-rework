package com.example.appointment.service;

import com.example.appointment.dto.SlotCreateRequest;
import com.example.appointment.exception.BadRequestException;
import com.example.appointment.exception.ErrorCode;
import com.example.appointment.repository.AppointmentRepository;
import com.example.appointment.repository.ProviderRepository;
import com.example.appointment.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    @Mock
    private TimeSlotRepository slotRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private SlotService slotService;

    @Test
    void create_startAfterEnd_throwsInvalidTimeRange() {
        SlotCreateRequest req = new SlotCreateRequest(
                1L, LocalDate.now().plusDays(1), LocalTime.of(10, 0), LocalTime.of(9, 0));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> slotService.create(req));
        assertEquals(ErrorCode.INVALID_TIME_RANGE, ex.getCode());
    }

    @Test
    void create_startEqualsEnd_throwsInvalidTimeRange() {
        SlotCreateRequest req = new SlotCreateRequest(
                1L, LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 0));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> slotService.create(req));
        assertEquals(ErrorCode.INVALID_TIME_RANGE, ex.getCode());
    }

    @Test
    void create_pastDate_throwsInvalidDate() {
        SlotCreateRequest req = new SlotCreateRequest(
                1L, LocalDate.now().minusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> slotService.create(req));
        assertEquals(ErrorCode.INVALID_DATE, ex.getCode());
    }
}
