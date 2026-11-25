package com.example.appointment.service;

import com.example.appointment.dto.CustomerRequest;
import com.example.appointment.dto.CustomerResponse;
import com.example.appointment.entity.Customer;
import com.example.appointment.exception.ConflictException;
import com.example.appointment.exception.ErrorCode;
import com.example.appointment.exception.ResourceNotFoundException;
import com.example.appointment.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw new ConflictException(
                    ErrorCode.DUPLICATE_EMAIL,
                    "A customer with email '" + request.email() + "' already exists"
            );
        }
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(Pageable pageable) {
        return customerRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(find(id));
    }

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CUSTOMER_NOT_FOUND, "Customer " + id + " not found"));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCreatedAt());
    }
}
