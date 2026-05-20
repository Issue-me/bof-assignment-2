package com.bof.banking.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for user profile responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String role;
    private boolean resident;
    private String tinNumber;
    private boolean active;
    private boolean seniorCitizen;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}
