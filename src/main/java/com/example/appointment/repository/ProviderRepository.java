package com.example.appointment.repository;

import com.example.appointment.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    boolean existsByEmail(String email);
}
