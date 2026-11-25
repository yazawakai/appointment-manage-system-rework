package com.example.appointment.service;

import com.example.appointment.dto.ProviderRequest;
import com.example.appointment.dto.ProviderResponse;
import com.example.appointment.entity.Provider;
import com.example.appointment.exception.ConflictException;
import com.example.appointment.exception.ErrorCode;
import com.example.appointment.exception.ResourceNotFoundException;
import com.example.appointment.repository.ProviderRepository;
import com.example.appointment.repository.TimeSlotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final TimeSlotRepository timeSlotRepository;

    public ProviderService(ProviderRepository providerRepository, TimeSlotRepository timeSlotRepository) {
        this.providerRepository = providerRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    @Transactional
    public ProviderResponse create(ProviderRequest request) {
        if (providerRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_EMAIL,
                    "A provider with email '" + request.email() + "' already exists"
            );
        }
        Provider provider = new Provider();
        provider.setName(request.name());
        provider.setEmail(request.email());
        provider.setPhone(request.phone());
        provider.setServiceCategory(request.serviceCategory());
        return toResponse(providerRepository.save(provider));
    }

    @Transactional(readOnly = true)
    public Page<ProviderResponse> list(Pageable pageable) {
        return providerRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProviderResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProviderResponse update(Long id, ProviderRequest request) {
        Provider provider = find(id);
        if (!provider.getEmail().equalsIgnoreCase(request.email()) && providerRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_EMAIL,
                    "A provider with email '" + request.email() + "' already exists"
            );
        }
        provider.setName(request.name());
        provider.setEmail(request.email());
        provider.setPhone(request.phone());
        provider.setServiceCategory(request.serviceCategory());
        return toResponse(provider);
    }

    @Transactional
    public void delete(Long id) {
        Provider provider = find(id);
        if (timeSlotRepository.existsByProviderId(id)) {
            throw new ConflictException(
                    ErrorCode.PROVIDER_HAS_SLOTS,
                    "Provider " + id + " still owns time slots and cannot be deleted"
            );
        }
        providerRepository.delete(provider);
    }

    private Provider find(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PROVIDER_NOT_FOUND, "Provider " + id + " not found"));
    }

    private ProviderResponse toResponse(Provider p) {
        return new ProviderResponse(p.getId(), p.getName(), p.getEmail(), p.getPhone(), p.getServiceCategory(), p.getCreatedAt());
    }
}
