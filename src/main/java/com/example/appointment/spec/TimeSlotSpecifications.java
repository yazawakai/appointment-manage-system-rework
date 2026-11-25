package com.example.appointment.spec;

import com.example.appointment.entity.SlotStatus;
import com.example.appointment.entity.TimeSlot;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Reusable {@link Specification} predicates for filtering time slots. Each
 * method returns {@code null} when the corresponding filter is absent, so the
 * predicates can be chained freely.
 */
public final class TimeSlotSpecifications {

    private TimeSlotSpecifications() {
    }

    public static Specification<TimeSlot> hasProviderId(Long providerId) {
        return (root, query, cb) ->
                providerId == null ? null : cb.equal(root.get("provider").get("id"), providerId);
    }

    public static Specification<TimeSlot> dateBetween(LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            if (startDate != null && endDate != null) {
                return cb.between(root.get("date"), startDate, endDate);
            }
            if (startDate != null) {
                return cb.greaterThanOrEqualTo(root.get("date"), startDate);
            }
            if (endDate != null) {
                return cb.lessThanOrEqualTo(root.get("date"), endDate);
            }
            return null;
        };
    }

    public static Specification<TimeSlot> hasCategory(String category) {
        return (root, query, cb) ->
                category == null ? null : cb.equal(root.get("provider").get("serviceCategory"), category);
    }

    public static Specification<TimeSlot> hasStatus(SlotStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * Excludes slots whose end time has already passed (an {@code AVAILABLE}
     * slot whose end time is before now is effectively expired).
     */
    public static Specification<TimeSlot> notExpired(LocalDate today, LocalTime now) {
        return (root, query, cb) -> cb.or(
                cb.greaterThan(root.get("date"), today),
                cb.and(cb.equal(root.get("date"), today), cb.greaterThan(root.get("endTime"), now))
        );
    }
}
