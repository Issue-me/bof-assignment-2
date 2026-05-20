package com.bof.banking.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for updating customer profile information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCustomerRequest {

    private String firstName;

    private String lastName;

    @Email(message = "Valid email is required")
    private String email;

    @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Phone number format is invalid")
    private String phoneNumber;

    private String tinNumber;

    @Pattern(regexp = "^[A-Za-z0-9/-]{4,20}$", message = "Passport number format is invalid")
    private String nationalId;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private String address;

    private Boolean isResident;
}
