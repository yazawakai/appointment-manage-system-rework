package com.example.appointment.service;

import com.example.appointment.dto.AppointmentResponse;
import com.example.appointment.dto.BookRequest;
import com.example.appointment.dto.CancelRequest;
import com.example.appointment.entity.Appointment;
import com.example.appointment.entity.AppointmentStatus;
import com.example.appointment.entity.Customer;
import com.example.appointment.entity.SlotStatus;
import com.example.appointment.entity.TimeSlot;
import com.example.appointment.exception.ConflictException;
import com.example.appointment.exception.ErrorCode;
import com.example.appointment.exception.ForbiddenException;
import com.example.appointment.exception.ResourceNotFoundException;
import com.example.appointment.repository.AppointmentRepository;
import com.example.appointment.repository.CustomerRepository;
import com.example.appointment.repository.TimeSlotRepository;
import com.example.appointment.spec.AppointmentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository slotRepository;
    private final CustomerRepository customerRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              TimeSlotRepository slotRepository,
                              CustomerRepository customerRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Books a slot. The optimistic {@code @Version} on the slot guarantees that
     * exactly one concurrent request wins; the loser receives a
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * which the global handler maps to HTTP 409.
     */
    @Transactional
    public AppointmentResponse book(BookRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CUSTOMER_NOT_FOUND, "Customer " + request.customerId() + " not found"));

        TimeSlot slot = slotRepository.findById(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SLOT_NOT_FOUND, "Slot " + request.slotId() + " not found"));

        if (isExpired(slot)) {
            throw new ConflictException(ErrorCode.SLOT_EXPIRED, "Slot " + slot.getId() + " has already expired");
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new ConflictException(
                    ErrorCode.SLOT_ALREADY_BOOKED,
                    "Slot " + slot.getId() + " is not available for booking (current status: " + slot.getStatus() + ")"
            );
        }

        slot.setStatus(SlotStatus.BOOKED);

        Appointment appointment = new Appointment();
        appointment.setSlot(slot);
        appointment.setCustomer(customer);
        appointment.setBookedAt(LocalDateTime.now());
        return toResponse(appointmentRepository.save(appointment));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> list(Long customerId, Long providerId, AppointmentStatus status, Pageable pageable) {
        Specification<Appointment> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and(AppointmentSpecifications.hasCustomerId(customerId))
                .and(AppointmentSpecifications.hasProviderId(providerId))
                .and(AppointmentSpecifications.hasStatus(status));
        return appointmentRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional
    public AppointmentResponse confirm(Long id) {
        Appointment appointment = find(id);
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE,
                    "Only BOOKED appointments can be confirmed (current: " + appointment.getStatus() + ")"
            );
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(LocalDateTime.now());
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse complete(Long id) {
        Appointment appointment = find(id);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE,
                    "Only CONFIRMED appointments can be completed (current: " + appointment.getStatus() + ")"
            );
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(LocalDateTime.now());
        return toResponse(appointment);
    }

    /**
     * Cancels an appointment and atomically releases the slot back to
     * AVAILABLE within the same transaction. Ownership and eligibility are
     * validated before the state change.
     */
    @Transactional
    public AppointmentResponse cancel(Long id, CancelRequest request) {
        Appointment appointment = find(id);

        if (!appointment.getCustomer().getId().equals(request.customerId())) {
            throw new ForbiddenException(
                    ErrorCode.CUSTOMER_NOT_OWNER,
                    "Appointment " + id + " does not belong to customer " + request.customerId()
            );
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELED) {
            throw new ConflictException(
                    ErrorCode.APPOINTMENT_ALREADY_CANCELED, "Appointment " + id + " is already canceled");
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE,
                    "Appointment " + id + " with status " + appointment.getStatus() + " cannot be canceled"
            );
        }

        appointment.setStatus(AppointmentStatus.CANCELED);
        appointment.setCanceledAt(LocalDateTime.now());
        appointment.setCancelReason(request.reason());
        appointment.getSlot().setStatus(SlotStatus.AVAILABLE);
        return toResponse(appointment);
    }

    private Appointment find(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.APPOINTMENT_NOT_FOUND, "Appointment " + id + " not found"));
    }

    private boolean isExpired(TimeSlot slot) {
        LocalDate today = LocalDate.now();
        if (slot.getDate().isBefore(today)) {
            return true;
        }
        return slot.getDate().isEqual(today) && !slot.getEndTime().isAfter(LocalTime.now());
    }

    private AppointmentResponse toResponse(Appointment a) {
        TimeSlot slot = a.getSlot();
        return new AppointmentResponse(
                a.getId(),
                a.getCustomer().getId(),
                slot.getId(),
                slot.getProvider().getId(),
                a.getStatus(),
                a.getBookedAt(),
                a.getConfirmedAt(),
                a.getCompletedAt(),
                a.getCanceledAt(),
                a.getCancelReason()
        );
    }
}
