package com.bof.banking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for mobile app login requests using customer ID.
 * Mobile app customers use numeric-only passwords for easier mobile input.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileLoginRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Password is required")
    @Pattern(regexp = "^[0-9]{8,}$", message = "Password must be at least 8 digits")
    private String password;
}
