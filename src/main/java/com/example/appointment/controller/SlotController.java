package com.example.appointment.controller;

import com.example.appointment.dto.SlotCreateRequest;
import com.example.appointment.dto.SlotResponse;
import com.example.appointment.entity.SlotStatus;
import com.example.appointment.service.SlotService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/slots")
@Tag(name = "Time Slots", description = "Provider availability slot management and customer search")
public class SlotController {

    private final SlotService service;

    public SlotController(SlotService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SlotResponse> create(@Valid @RequestBody SlotCreateRequest request) {
        SlotResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/slots/" + created.id())).body(created);
    }

    @GetMapping
    public Page<SlotResponse> search(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SlotStatus status,
            Pageable pageable) {
        return service.search(providerId, startDate, endDate, category, status, pageable);
    }

    @GetMapping("/available")
    public Page<SlotResponse> available(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        return service.searchAvailable(providerId, startDate, endDate, category, pageable);
    }

    @GetMapping("/{id}")
    public SlotResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
