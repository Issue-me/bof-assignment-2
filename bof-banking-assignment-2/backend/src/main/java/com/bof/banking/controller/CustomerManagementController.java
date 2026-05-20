package com.bof.banking.controller;

import com.bof.banking.dto.user.CreateCustomerRequest;
import com.bof.banking.dto.user.CustomerDetailResponse;
import com.bof.banking.dto.user.UpdateCustomerRequest;
import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.service.CustomerManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for customer profile management (ADMIN/TELLER only).
 * <p>
 * This controller manages customer profiles and their account relationships,
 * distinct from UserController which handles system-wide user operations.
 * </p>
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerManagementController {

    private final CustomerManagementService customerManagementService;

    /**
     * Get all customers in the system.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllCustomers() {
        List<UserResponse> customers = customerManagementService.getAllCustomers();
        return ResponseEntity.ok(customers);
    }

    /**
     * Get detailed customer profile including linked accounts.
     */
    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<CustomerDetailResponse> getCustomerDetail(@PathVariable Long customerId) {
        CustomerDetailResponse detail = customerManagementService.getCustomerDetail(customerId);
        return ResponseEntity.ok(detail);
    }

    /**
     * Create a new customer profile.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<UserResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        UserResponse customer = customerManagementService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }

    /**
     * Update customer profile information.
     */
    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER','ADMIN')")
    public ResponseEntity<UserResponse> updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody UpdateCustomerRequest request) {
        UserResponse updated = customerManagementService.updateCustomer(customerId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deactivate a customer profile (soft delete).
     */
    @PostMapping("/{customerId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable Long customerId) {
        customerManagementService.deactivateCustomer(customerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reactivate a customer profile.
     */
    @PostMapping("/{customerId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateCustomer(@PathVariable Long customerId) {
        customerManagementService.activateCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
