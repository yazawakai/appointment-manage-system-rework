package com.example.appointment.controller;

import com.example.appointment.dto.ProviderRequest;
import com.example.appointment.dto.ProviderResponse;
import com.example.appointment.service.ProviderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/providers")
@Tag(name = "Providers", description = "Service provider management")
public class ProviderController {

    private final ProviderService service;

    public ProviderController(ProviderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProviderResponse> create(@Valid @RequestBody ProviderRequest request) {
        ProviderResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/providers/" + created.id())).body(created);
    }

    @GetMapping
    public Page<ProviderResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public ProviderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ProviderResponse update(@PathVariable Long id, @Valid @RequestBody ProviderRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
