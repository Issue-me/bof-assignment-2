package com.bof.banking.service;

import com.bof.banking.dto.user.UserResponse;

import java.util.List;

/**
 * Service interface for user management operations.
 */
public interface UserService {

    UserResponse getUserById(Long id);

    UserResponse getUserByEmail(String email);

    UserResponse getUserByCustomerId(String customerId);

    List<UserResponse> getAllUsers();

    UserResponse updateUser(Long id, UserResponse userResponse);

    void deactivateUser(Long id);

    void activateUser(Long id);
}
