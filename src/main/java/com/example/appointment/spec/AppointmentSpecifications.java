package com.example.appointment.spec;

import com.example.appointment.entity.Appointment;
import com.example.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification} predicates for filtering appointments.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    public static Specification<Appointment> hasCustomerId(Long customerId) {
        return (root, query, cb) ->
                customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Appointment> hasProviderId(Long providerId) {
        return (root, query, cb) ->
                providerId == null ? null : cb.equal(root.get("slot").get("provider").get("id"), providerId);
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }
}
