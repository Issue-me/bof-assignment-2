package com.bof.banking.service.impl;

import com.bof.banking.config.BankingProperties;
import com.bof.banking.dto.auth.LoginRequest;
import com.bof.banking.dto.auth.LoginResponse;
import com.bof.banking.dto.auth.MobileLoginRequest;
import com.bof.banking.dto.auth.RegisterRequest;
import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.exception.BadRequestException;
import com.bof.banking.exception.ResourceNotFoundException;
import com.bof.banking.model.Role;
import com.bof.banking.model.User;
import com.bof.banking.repository.UserRepository;
import com.bof.banking.security.JwtService;
import com.bof.banking.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementation of {@link AuthService} for authentication and user registration operations.
 * <p>
 * This service handles user authentication via JWT tokens, new user registration,
 * and profile retrieval. Customer IDs are generated using configurable prefixes
 * from {@link BankingProperties}.
 * </p>
 *
 * @author Bank of Fiji Development Team
 * @version 1.0.0
 * @see AuthService
 * @see JwtService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final BankingProperties bankingProperties;

    /**
     * Handles login.
     * @param request the request payload.
     * @return the result of the operation.
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User {} logged in successfully", user.getEmail());

        return LoginResponse.builder()
                .token(token)
                .customerId(user.getCustomerId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .message("Login successful")
                .build();
    }

    /**
     * Authenticates a customer using customer ID and password for mobile app.
     * <p>
     * Mobile app uses customer ID instead of email for login. Only customers
     * (role=CUSTOMER) can login via this endpoint. Passwords must be numeric.
     * </p>
     *
     * @param request the mobile login credentials
     * @return login response containing JWT token and user details
     * @throws ResourceNotFoundException if customer is not found
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are invalid
     */
    @Override
    @Transactional
    public LoginResponse mobileLogin(MobileLoginRequest request) {
        User user = userRepository.findByCustomerId(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // Verify customer role
        if (user.getRole() != Role.CUSTOMER) {
            throw new BadRequestException("Mobile login is only available for customers");
        }

        // Authenticate using the email internally (Spring Security uses email as username)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("Customer {} logged in via mobile app", user.getCustomerId());

        return LoginResponse.builder()
                .token(token)
                .customerId(user.getCustomerId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .message("Login successful")
                .build();
    }

    @Override
    @Transactional
    /**
     * Handles register.
     * @param request the request payload.
     * @return the result of the operation.
     */
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already in use: " + request.getEmail());
        }

        String customerId = generateCustomerId();

        User user = User.builder()
                .customerId(customerId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(Role.CUSTOMER)
                .isActive(true)
                .isResident(true)
                .build();

        userRepository.save(user);
        log.info("New customer registered: {} - {}", customerId, user.getEmail());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);

        return LoginResponse.builder()
                .token(token)
                .customerId(customerId)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .message("Registration successful")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    /**
     * Returns profile data.
     * @param email the email of the authenticated user.
     * @return the result of the operation.
     */
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserResponse.builder()
                .id(user.getId())
                .customerId(user.getCustomerId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .role(user.getRole().name())
                .resident(user.isResident())
                .tinNumber(user.getTinNumber())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .seniorCitizen(user.isSeniorCitizen())
                .build();
    }

    /**
     * Generates a unique customer ID using the configured prefix.
     * <p>
     * The format is "{prefix}-{6-digit-number}" (e.g., "BOF-000001").
     * The prefix is configurable via {@link BankingProperties}.
     * </p>
     *
     * @return a unique customer ID
     */
    private String generateCustomerId() {
        long count = userRepository.count() + 1;
        return String.format("%s-%06d", bankingProperties.getCustomerIdPrefix(), count);
    }
}
