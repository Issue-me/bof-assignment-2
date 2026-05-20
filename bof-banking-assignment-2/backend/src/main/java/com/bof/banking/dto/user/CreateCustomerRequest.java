package com.bof.banking.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for creating a new customer profile by admin/teller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomerRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Phone number format is invalid")
    private String phoneNumber;

    @NotBlank(message = "Initial password is required")
    private String password;

    private String tinNumber;

    @NotBlank(message = "Passport number is required")
    @Pattern(regexp = "^[A-Za-z0-9/-]{4,20}$", message = "Passport number format is invalid")
    private String nationalId;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String address;

    @Builder.Default
    private boolean isResident = true;
}
