package com.example.appointment.controller;

import com.example.appointment.dto.AppointmentResponse;
import com.example.appointment.dto.BookRequest;
import com.example.appointment.dto.CancelRequest;
import com.example.appointment.entity.AppointmentStatus;
import com.example.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Full appointment lifecycle: booking, confirm, complete, cancel")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookRequest request) {
        AppointmentResponse created = service.book(request);
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id())).body(created);
    }

    @GetMapping
    public Page<AppointmentResponse> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) AppointmentStatus status,
            Pageable pageable) {
        return service.list(customerId, providerId, status, pageable);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/confirm")
    public AppointmentResponse confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/complete")
    public AppointmentResponse complete(@PathVariable Long id) {
        return service.complete(id);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request);
    }
}
