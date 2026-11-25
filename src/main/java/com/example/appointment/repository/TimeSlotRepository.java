package com.example.appointment.repository;

import com.example.appointment.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long>, JpaSpecificationExecutor<TimeSlot> {

    /** All slots of a provider on a given day, used for the overlap check. */
    List<TimeSlot> findByProviderIdAndDate(Long providerId, LocalDate date);

    /** Whether a provider owns at least one slot (guards provider deletion). */
    boolean existsByProviderId(Long providerId);
}
