package com.bof.banking.controller;

import com.bof.banking.dto.auth.LoginRequest;
import com.bof.banking.dto.auth.LoginResponse;
import com.bof.banking.dto.auth.MobileLoginRequest;
import com.bof.banking.dto.auth.RegisterRequest;
import com.bof.banking.dto.user.UserResponse;
import com.bof.banking.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    /**
     * Handles login.
     * @param request the request payload.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mobile/login")
    /**
     * Handles mobile login.
     * @param request the request payload.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<LoginResponse> mobileLogin(@Valid @RequestBody MobileLoginRequest request) {
        LoginResponse response = authService.mobileLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    /**
     * Handles register.
     * @param request the request payload.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/profile")
    /**
     * Returns profile data.
     * @param userDetails the authenticated user context.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse profile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/logout")
    /**
     * Handles logout.
     * @return an HTTP response containing the operation result.
     */
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
