package com.bof.banking.service;

import com.bof.banking.dto.user.CreateCustomerRequest;
import com.bof.banking.dto.user.CustomerDetailResponse;
import com.bof.banking.dto.user.UpdateCustomerRequest;
import com.bof.banking.dto.user.UserResponse;

import java.util.List;

/**
 * Service interface for customer profile management operations.
 */
public interface CustomerManagementService {

    /**
     * Get all customers (users with CUSTOMER role).
     */
    List<UserResponse> getAllCustomers();

    /**
     * Get detailed customer profile including linked accounts.
     */
    CustomerDetailResponse getCustomerDetail(Long customerId);

    /**
     * Create a new customer profile.
     */
    UserResponse createCustomer(CreateCustomerRequest request);

    /**
     * Update customer profile information.
     */
    UserResponse updateCustomer(Long customerId, UpdateCustomerRequest request);

    /**
     * Deactivate a customer (soft delete).
     */
    void deactivateCustomer(Long customerId);

    /**
     * Reactivate a customer.
     */
    void activateCustomer(Long customerId);
}
