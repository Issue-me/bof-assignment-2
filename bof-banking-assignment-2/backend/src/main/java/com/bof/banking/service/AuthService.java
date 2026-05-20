package com.bof.banking.service;

import com.bof.banking.dto.auth.LoginRequest;
import com.bof.banking.dto.auth.LoginResponse;
import com.bof.banking.dto.auth.MobileLoginRequest;
import com.bof.banking.dto.auth.RegisterRequest;
import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;

/**
 * Service interface for authentication and user registration operations.
 * <p>
 * Provides methods for user login, registration, and profile retrieval.
 * Authentication is handled via JWT tokens.
 * </p>
 *
 * @author Bank of Fiji Development Team
 * @version 1.0.0
 */
public interface AuthService {

    /**
     * Authenticates a user with email and password (web app).
     *
     * @param request the login credentials
     * @return login response containing JWT token and user details
     * @throws ResourceNotFoundException if user is not found
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are invalid
     */
    LoginResponse login(LoginRequest request);

    /**
     * Authenticates a customer with customer ID and numeric password (mobile app).
     *
     * @param request the mobile login credentials
     * @return login response containing JWT token and user details
     * @throws ResourceNotFoundException if customer is not found
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are invalid
     */
    LoginResponse mobileLogin(MobileLoginRequest request);

    /**
     * Registers a new customer account.
     * <p>
     * A unique customer ID is auto-generated. The user is assigned
     * the CUSTOMER role by default.
     * </p>
     *
     * @param request the registration details
     * @return login response containing JWT token and user details
     * @throws BadRequestException if email is already in use
     */
    LoginResponse register(RegisterRequest request);

    /**
     * Retrieves the profile of a user by email.
     *
     * @param email the user's email address
     * @return the user's profile details
     * @throws ResourceNotFoundException if user is not found
     */
    UserResponse getProfile(String email);
}
