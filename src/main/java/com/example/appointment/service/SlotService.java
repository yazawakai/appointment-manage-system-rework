package com.example.appointment.service;

import com.example.appointment.dto.SlotCreateRequest;
import com.example.appointment.dto.SlotResponse;
import com.example.appointment.entity.Provider;
import com.example.appointment.entity.SlotStatus;
import com.example.appointment.entity.TimeSlot;
import com.example.appointment.exception.BadRequestException;
import com.example.appointment.exception.ConflictException;
import com.example.appointment.exception.ErrorCode;
import com.example.appointment.exception.ResourceNotFoundException;
import com.example.appointment.repository.AppointmentRepository;
import com.example.appointment.repository.ProviderRepository;
import com.example.appointment.repository.TimeSlotRepository;
import com.example.appointment.spec.TimeSlotSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class SlotService {

    private final TimeSlotRepository slotRepository;
    private final ProviderRepository providerRepository;
    private final AppointmentRepository appointmentRepository;

    public SlotService(TimeSlotRepository slotRepository,
                       ProviderRepository providerRepository,
                       AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.providerRepository = providerRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public SlotResponse create(SlotCreateRequest request) {
        validateTimeRange(request.startTime(), request.endTime());
        validateNotPast(request.date(), request.startTime());

        Provider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PROVIDER_NOT_FOUND, "Provider " + request.providerId() + " not found"));

        List<TimeSlot> sameDay = slotRepository.findByProviderIdAndDate(request.providerId(), request.date());
        for (TimeSlot existing : sameDay) {
            if (overlaps(request.startTime(), request.endTime(), existing.getStartTime(), existing.getEndTime())) {
                throw new ConflictException(
                        ErrorCode.SLOT_OVERLAP,
                        "Slot overlaps with existing slot " + existing.getId()
                                + " (" + existing.getStartTime() + "-" + existing.getEndTime()
                                + ") of provider " + request.providerId()
                );
            }
        }

        TimeSlot slot = new TimeSlot();
        slot.setProvider(provider);
        slot.setDate(request.date());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        return toResponse(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public Page<SlotResponse> search(Long providerId, LocalDate startDate, LocalDate endDate,
                                     String category, SlotStatus status, Pageable pageable) {
        Specification<TimeSlot> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(TimeSlotSpecifications.hasProviderId(providerId))
                .and(TimeSlotSpecifications.dateBetween(startDate, endDate))
                .and(TimeSlotSpecifications.hasCategory(category))
                .and(TimeSlotSpecifications.hasStatus(status));
        return slotRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<SlotResponse> searchAvailable(Long providerId, LocalDate startDate, LocalDate endDate,
                                              String category, Pageable pageable) {
        Specification<TimeSlot> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(TimeSlotSpecifications.hasProviderId(providerId))
                .and(TimeSlotSpecifications.dateBetween(startDate, endDate))
                .and(TimeSlotSpecifications.hasCategory(category))
                .and(TimeSlotSpecifications.hasStatus(SlotStatus.AVAILABLE))
                .and(TimeSlotSpecifications.notExpired(LocalDate.now(), LocalTime.now()));
        return slotRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SlotResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public void delete(Long id) {
        TimeSlot slot = find(id);
        if (slot.getStatus() == SlotStatus.BOOKED || appointmentRepository.existsBySlotId(id)) {
            throw new ConflictException(
                    ErrorCode.SLOT_ALREADY_BOOKED, "Slot " + id + " is booked and cannot be deleted");
        }
        slotRepository.delete(slot);
    }

    private TimeSlot find(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SLOT_NOT_FOUND, "Slot " + id + " not found"));
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            throw new BadRequestException(ErrorCode.INVALID_TIME_RANGE, "startTime must be before endTime");
        }
    }

    private void validateNotPast(LocalDate date, LocalTime start) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new BadRequestException(ErrorCode.INVALID_DATE, "Slot date must be today or in the future");
        }
        if (date.isEqual(today) && !start.isAfter(LocalTime.now())) {
            throw new BadRequestException(ErrorCode.INVALID_DATE, "Slot start time must be in the future");
        }
    }

    private boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private SlotResponse toResponse(TimeSlot s) {
        return new SlotResponse(s.getId(), s.getProvider().getId(), s.getDate(), s.getStartTime(), s.getEndTime(), s.getStatus());
    }
}
